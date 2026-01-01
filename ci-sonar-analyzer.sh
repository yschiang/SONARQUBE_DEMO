#!/bin/bash
set -euo pipefail

# SonarQube CI/CD Analysis Summary Generator (Shell Version)
# Usage: ./ci-sonar-analyzer.sh [options]

# Default configuration
SONAR_URL="${SONAR_URL:-http://localhost:9999}"
SONAR_TOKEN="${SONAR_TOKEN:-}"
PROJECT_KEY="${PROJECT_KEY:-}"
FORMAT="${FORMAT:-text}"
OUTPUT_FILE="${OUTPUT_FILE:-}"
WAIT_TIME="${WAIT_TIME:-0}"
SET_EXIT_CODE="${SET_EXIT_CODE:-false}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Help function
show_help() {
    cat << EOF
SonarQube CI/CD Analysis Summary Generator

Usage: $0 [options]

Options:
    --server-url URL    SonarQube server URL (default: $SONAR_URL)
    --token TOKEN       SonarQube authentication token (required)
    --project-key KEY   SonarQube project key (required)
    --format FORMAT     Output format: text|json (default: text)
    --output FILE       Output file (default: stdout)
    --wait TIME         Wait N seconds for analysis completion
    --exit-code         Set exit code based on analysis results
    --help              Show this help message

Environment Variables:
    SONAR_URL, SONAR_TOKEN, PROJECT_KEY, FORMAT, OUTPUT_FILE, WAIT_TIME

Examples:
    $0 --token abc123 --project-key my-project
    $0 --token abc123 --project-key my-project --format json --output report.json
    $0 --token abc123 --project-key my-project --exit-code
EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --server-url)
            SONAR_URL="$2"
            shift 2
            ;;
        --token)
            SONAR_TOKEN="$2"
            shift 2
            ;;
        --project-key)
            PROJECT_KEY="$2"
            shift 2
            ;;
        --format)
            FORMAT="$2"
            shift 2
            ;;
        --output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        --wait)
            WAIT_TIME="$2"
            shift 2
            ;;
        --exit-code)
            SET_EXIT_CODE="true"
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            show_help >&2
            exit 1
            ;;
    esac
done

# Validate required parameters
if [[ -z "$SONAR_TOKEN" ]]; then
    echo -e "${RED}❌ Error: SonarQube token is required${NC}" >&2
    echo "Use --token TOKEN or set SONAR_TOKEN environment variable" >&2
    exit 1
fi

if [[ -z "$PROJECT_KEY" ]]; then
    echo -e "${RED}❌ Error: Project key is required${NC}" >&2
    echo "Use --project-key KEY or set PROJECT_KEY environment variable" >&2
    exit 1
fi

# Check dependencies
if ! command -v curl &> /dev/null; then
    echo -e "${RED}❌ Error: curl is required but not installed${NC}" >&2
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}⚠️  Warning: jq not found, JSON parsing will be limited${NC}" >&2
fi

# Wait for analysis if requested
if [[ "$WAIT_TIME" -gt 0 ]]; then
    echo -e "${BLUE}⏳ Waiting $WAIT_TIME seconds for analysis to complete...${NC}" >&2
    sleep "$WAIT_TIME"
fi

# Helper function to make API calls
api_call() {
    local endpoint="$1"
    local params="$2"
    local url="${SONAR_URL}/api/${endpoint}"
    
    if [[ -n "$params" ]]; then
        url="${url}?${params}"
    fi
    
    curl -s -H "Authorization: Bearer ${SONAR_TOKEN}" "$url" 2>/dev/null
}

# Get project metrics
get_metrics() {
    local params="component=${PROJECT_KEY}&metricKeys=ncloc,bugs,vulnerabilities,code_smells,sqale_rating,reliability_rating,security_rating,coverage,duplicated_lines_density"
    api_call "measures/component" "$params"
}

# Get issues
get_issues() {
    local params="componentKeys=${PROJECT_KEY}&ps=500&resolved=false"
    api_call "issues/search" "$params"
}

# Get quality gate status
get_quality_gate() {
    local params="projectKey=${PROJECT_KEY}"
    api_call "qualitygates/project_status" "$params"
}

# Extract value from JSON (simple grep/sed approach for systems without jq)
extract_json_value() {
    local json="$1"
    local key="$2"
    
    if command -v jq &> /dev/null; then
        echo "$json" | jq -r "$key // \"N/A\""
    else
        # Fallback for systems without jq
        echo "$json" | grep -o "\"$key\":\"[^\"]*\"" | sed "s/\"$key\":\"\([^\"]*\)\"/\1/" || echo "N/A"
    fi
}

# Count issues by severity (simple approach)
count_issues() {
    local issues_json="$1"
    local severity="$2"
    
    if command -v jq &> /dev/null; then
        echo "$issues_json" | jq -r "[.issues[] | select(.severity == \"$severity\")] | length"
    else
        echo "$issues_json" | grep -o "\"severity\":\"$severity\"" | wc -l | tr -d ' '
    fi
}

# Format rating with emoji
format_rating() {
    local rating="$1"
    case "$rating" in
        "1.0") echo "A ⭐" ;;
        "2.0") echo "B 🟢" ;;
        "3.0") echo "C 🟡" ;;
        "4.0") echo "D 🟠" ;;
        "5.0") echo "E 🔴" ;;
        *) echo "$rating" ;;
    esac
}

# Generate text summary
generate_text_summary() {
    local timestamp=$(date "+%Y-%m-%d %H:%M:%S")
    
    echo -e "${BLUE}🔍 Fetching SonarQube analysis data...${NC}" >&2
    
    # Get data
    local metrics_json=$(get_metrics)
    local issues_json=$(get_issues)
    local quality_gate_json=$(get_quality_gate)
    
    # Extract metrics
    local ncloc coverage duplicated_lines
    local sqale_rating reliability_rating security_rating
    
    if command -v jq &> /dev/null && [[ -n "$metrics_json" ]]; then
        ncloc=$(echo "$metrics_json" | jq -r '.component.measures[] | select(.metric=="ncloc") | .value // "N/A"')
        coverage=$(echo "$metrics_json" | jq -r '.component.measures[] | select(.metric=="coverage") | .value // "N/A"')
        duplicated_lines=$(echo "$metrics_json" | jq -r '.component.measures[] | select(.metric=="duplicated_lines_density") | .value // "N/A"')
        sqale_rating=$(echo "$metrics_json" | jq -r '.component.measures[] | select(.metric=="sqale_rating") | .value // "N/A"')
        reliability_rating=$(echo "$metrics_json" | jq -r '.component.measures[] | select(.metric=="reliability_rating") | .value // "N/A"')
        security_rating=$(echo "$metrics_json" | jq -r '.component.measures[] | select(.metric=="security_rating") | .value // "N/A"')
    else
        ncloc="N/A"
        coverage="N/A"
        duplicated_lines="N/A"
        sqale_rating="N/A"
        reliability_rating="N/A"
        security_rating="N/A"
    fi
    
    # Extract quality gate status
    local qg_status="UNKNOWN"
    if command -v jq &> /dev/null && [[ -n "$quality_gate_json" ]]; then
        qg_status=$(echo "$quality_gate_json" | jq -r '.projectStatus.status // "UNKNOWN"')
    fi
    
    # Count issues
    local total_issues=0
    local blocker_count=0
    local critical_count=0
    local major_count=0
    local minor_count=0
    local info_count=0
    local bug_count=0
    local vulnerability_count=0
    local code_smell_count=0
    
    if [[ -n "$issues_json" ]]; then
        if command -v jq &> /dev/null; then
            total_issues=$(echo "$issues_json" | jq -r '.issues | length')
            blocker_count=$(count_issues "$issues_json" "BLOCKER")
            critical_count=$(count_issues "$issues_json" "CRITICAL")
            major_count=$(count_issues "$issues_json" "MAJOR")
            minor_count=$(count_issues "$issues_json" "MINOR")
            info_count=$(count_issues "$issues_json" "INFO")
            bug_count=$(echo "$issues_json" | jq -r '[.issues[] | select(.type == "BUG")] | length')
            vulnerability_count=$(echo "$issues_json" | jq -r '[.issues[] | select(.type == "VULNERABILITY")] | length')
            code_smell_count=$(echo "$issues_json" | jq -r '[.issues[] | select(.type == "CODE_SMELL")] | length')
        else
            total_issues=$(echo "$issues_json" | grep -o '"severity":' | wc -l | tr -d ' ')
        fi
    fi
    
    # Generate summary
    local qg_emoji="❌"
    [[ "$qg_status" == "OK" ]] && qg_emoji="✅"
    
    cat << EOF
════════════════════════════════════════════════════════════════
                    SONARQUBE CI ANALYSIS SUMMARY
════════════════════════════════════════════════════════════════
📅 Analysis Time: $timestamp
🏗️  Project: $PROJECT_KEY
🎯 Quality Gate: $qg_status $qg_emoji

📊 CODE METRICS:
├── Lines of Code: $ncloc
├── Coverage: ${coverage}%
├── Duplicated Lines: ${duplicated_lines}%
└── Quality Ratings:
    ├── Maintainability: $(format_rating "$sqale_rating")
    ├── Reliability: $(format_rating "$reliability_rating")
    └── Security: $(format_rating "$security_rating")

🚨 ISSUES SUMMARY:
├── Total Issues: $total_issues
├── By Severity:
│   ├── 🔴 BLOCKER: $blocker_count
│   ├── 🟠 CRITICAL: $critical_count
│   ├── 🟡 MAJOR: $major_count
│   ├── 🔵 MINOR: $minor_count
│   └── ⚪ INFO: $info_count
└── By Type:
    ├── 🐛 BUGS: $bug_count
    ├── 🔒 VULNERABILITIES: $vulnerability_count
    └── 💨 CODE SMELLS: $code_smell_count

🎯 CI/CD DECISION:
EOF

    # CI Decision
    if [[ "$qg_status" != "OK" ]]; then
        echo "❌ FAIL - Quality Gate failed"
    elif [[ "$blocker_count" -gt 0 ]]; then
        echo "❌ FAIL - $blocker_count BLOCKER issue(s) found"
    elif [[ "$critical_count" -gt 0 ]]; then
        echo "⚠️  WARNING - $critical_count CRITICAL issue(s) found"
    else
        echo "✅ PASS - No blocking issues found"
    fi

    echo
    echo "🌐 Dashboard: $SONAR_URL/dashboard?id=$PROJECT_KEY"
    echo "════════════════════════════════════════════════════════════════"
}

# Generate JSON summary
generate_json_summary() {
    echo -e "${BLUE}🔍 Fetching SonarQube analysis data...${NC}" >&2
    
    local metrics_json=$(get_metrics)
    local issues_json=$(get_issues)
    local quality_gate_json=$(get_quality_gate)
    
    if ! command -v jq &> /dev/null; then
        echo -e "${RED}❌ Error: jq is required for JSON output format${NC}" >&2
        exit 1
    fi
    
    # Extract quality gate status
    local qg_status="UNKNOWN"
    if [[ -n "$quality_gate_json" ]]; then
        qg_status=$(echo "$quality_gate_json" | jq -r '.projectStatus.status // "UNKNOWN"')
    fi
    
    # Count issues
    local total_issues=0
    local blocker_count=0
    local critical_count=0
    
    if [[ -n "$issues_json" ]]; then
        total_issues=$(echo "$issues_json" | jq -r '.issues | length')
        blocker_count=$(count_issues "$issues_json" "BLOCKER")
        critical_count=$(count_issues "$issues_json" "CRITICAL")
    fi
    
    # Build JSON summary
    cat << EOF | jq .
{
  "timestamp": "$(date -Iseconds)",
  "project": "$PROJECT_KEY",
  "quality_gate": {
    "status": "$qg_status",
    "passed": $([ "$qg_status" == "OK" ] && echo "true" || echo "false")
  },
  "metrics": $(echo "$metrics_json" | jq '.component.measures // []'),
  "issues": {
    "total": $total_issues,
    "by_severity": {
      "BLOCKER": $blocker_count,
      "CRITICAL": $critical_count
    }
  },
  "ci_decision": {
    "should_fail": $([ "$qg_status" != "OK" ] || [ "$blocker_count" -gt 0 ] && echo "true" || echo "false"),
    "has_warnings": $([ "$critical_count" -gt 0 ] && echo "true" || echo "false"),
    "is_passing": $([ "$qg_status" == "OK" ] && [ "$blocker_count" -eq 0 ] && echo "true" || echo "false")
  },
  "dashboard_url": "$SONAR_URL/dashboard?id=$PROJECT_KEY"
}
EOF
}

# Get exit code for CI
get_exit_code() {
    local quality_gate_json=$(get_quality_gate)
    local issues_json=$(get_issues)
    
    # Check quality gate
    if [[ -n "$quality_gate_json" ]] && command -v jq &> /dev/null; then
        local qg_status=$(echo "$quality_gate_json" | jq -r '.projectStatus.status // "ERROR"')
        if [[ "$qg_status" != "OK" ]]; then
            return 1
        fi
    fi
    
    # Check for blocker issues
    if [[ -n "$issues_json" ]]; then
        local blocker_count=$(count_issues "$issues_json" "BLOCKER")
        if [[ "$blocker_count" -gt 0 ]]; then
            return 1
        fi
    fi
    
    return 0
}

# Main execution
main() {
    local summary=""
    
    case "$FORMAT" in
        "json")
            summary=$(generate_json_summary)
            ;;
        "text"|*)
            summary=$(generate_text_summary)
            ;;
    esac
    
    # Output
    if [[ -n "$OUTPUT_FILE" ]]; then
        echo "$summary" > "$OUTPUT_FILE"
        echo -e "${GREEN}✅ Summary written to $OUTPUT_FILE${NC}" >&2
    else
        echo "$summary"
    fi
    
    # Set exit code if requested
    if [[ "$SET_EXIT_CODE" == "true" ]]; then
        if get_exit_code; then
            echo -e "${GREEN}🎯 Exit code: 0 (success)${NC}" >&2
            exit 0
        else
            echo -e "${RED}🎯 Exit code: 1 (failure)${NC}" >&2
            exit 1
        fi
    fi
}

# Run main function
main