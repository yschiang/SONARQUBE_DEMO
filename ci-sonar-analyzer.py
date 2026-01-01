#!/usr/bin/env python3
"""
SonarQube CI/CD Analysis Summary Generator
Automatically generates analysis summaries for CI pipelines
Usage: python ci-sonar-analyzer.py [options]
"""

import requests
import json
import sys
import argparse
import time
from datetime import datetime
from typing import Dict, List, Optional

class SonarQubeCIAnalyzer:
    def __init__(self, server_url: str, token: str, project_key: str):
        self.server_url = server_url.rstrip('/')
        self.token = token
        self.project_key = project_key
        self.headers = {"Authorization": f"Bearer {token}"}
    
    def make_request(self, endpoint: str, params: Optional[Dict] = None) -> Optional[Dict]:
        """Make authenticated request to SonarQube API"""
        url = f"{self.server_url}/api/{endpoint}"
        try:
            response = requests.get(url, headers=self.headers, params=params or {}, timeout=30)
            if response.status_code == 200:
                return response.json()
            else:
                print(f"❌ API Error {response.status_code}: {response.text}", file=sys.stderr)
                return None
        except Exception as e:
            print(f"❌ Request failed: {e}", file=sys.stderr)
            return None

    def get_project_metrics(self) -> Dict:
        """Get project quality metrics"""
        params = {
            "component": self.project_key,
            "metricKeys": "ncloc,bugs,vulnerabilities,code_smells,sqale_rating,reliability_rating,security_rating,coverage,duplicated_lines_density,new_bugs,new_vulnerabilities,new_code_smells"
        }
        return self.make_request("measures/component", params)

    def get_issues(self) -> Dict:
        """Get all project issues"""
        params = {
            "componentKeys": self.project_key,
            "ps": "500",
            "resolved": "false"
        }
        return self.make_request("issues/search", params)

    def get_quality_gate_status(self) -> Dict:
        """Get quality gate status"""
        params = {"projectKey": self.project_key}
        return self.make_request("qualitygates/project_status", params)

    def analyze_issues(self, issues_data: Dict) -> Dict:
        """Analyze and categorize issues"""
        if not issues_data:
            return {}
        
        issues = issues_data.get("issues", [])
        
        analysis = {
            "total": len(issues),
            "by_severity": {"BLOCKER": 0, "CRITICAL": 0, "MAJOR": 0, "MINOR": 0, "INFO": 0},
            "by_type": {"BUG": 0, "VULNERABILITY": 0, "CODE_SMELL": 0},
            "by_category": {"RELIABILITY": 0, "SECURITY": 0, "MAINTAINABILITY": 0},
            "category_details": {
                "RELIABILITY": [],
                "SECURITY": [], 
                "MAINTAINABILITY": []
            },
            "critical_issues": [],
            "new_issues": 0
        }
        
        for issue in issues:
            severity = issue.get("severity", "UNKNOWN")
            issue_type = issue.get("type", "UNKNOWN")
            rule_key = issue.get("rule", "")
            message = issue.get("message", "")
            
            analysis["by_severity"][severity] = analysis["by_severity"].get(severity, 0) + 1
            analysis["by_type"][issue_type] = analysis["by_type"].get(issue_type, 0) + 1
            
            # Categorize by quality dimension
            category = self._get_issue_category(issue_type, rule_key, message)
            analysis["by_category"][category] = analysis["by_category"].get(category, 0) + 1
            
            # Store issue details for reporting
            issue_detail = {
                "rule": rule_key,
                "message": message,
                "severity": severity,
                "component": issue.get("component", "").split(":")[-1],  # Get filename
                "line": issue.get("line", "N/A")
            }
            analysis["category_details"][category].append(issue_detail)
            
            # Track critical issues for CI decisions
            if severity in ["BLOCKER", "CRITICAL"]:
                analysis["critical_issues"].append({
                    "rule": issue.get("rule"),
                    "severity": severity,
                    "message": issue.get("message"),
                    "file": issue.get("component", "").split(":")[-1],
                    "line": issue.get("line")
                })
            
            # Count new issues (if available)
            if issue.get("isNew", False):
                analysis["new_issues"] += 1
        
        return analysis

    def generate_ci_summary(self, format_type: str = "text") -> str:
        """Generate CI-friendly summary report"""
        print("🔍 Fetching SonarQube analysis data...", file=sys.stderr)
        
        # Gather data
        metrics_data = self.get_project_metrics()
        issues_data = self.get_issues()
        quality_gate = self.get_quality_gate_status()
        
        if not issues_data:
            return "❌ Failed to retrieve analysis data"
        
        issue_analysis = self.analyze_issues(issues_data)
        
        if format_type == "json":
            return self._generate_json_summary(metrics_data, issue_analysis, quality_gate)
        elif format_type == "markdown":
            return self._generate_markdown_summary(metrics_data, issue_analysis, quality_gate)
        else:
            return self._generate_text_summary(metrics_data, issue_analysis, quality_gate)

    def _generate_text_summary(self, metrics_data: Dict, issue_analysis: Dict, quality_gate: Dict) -> str:
        """Generate plain text summary for CI logs"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        
        # Extract metrics
        measures = {}
        if metrics_data and "component" in metrics_data:
            for measure in metrics_data["component"].get("measures", []):
                measures[measure["metric"]] = measure.get("value", "N/A")
        
        # Quality Gate Status
        qg_status = "UNKNOWN"
        if quality_gate and "projectStatus" in quality_gate:
            qg_status = quality_gate["projectStatus"].get("status", "UNKNOWN")
        
        # Generate summary
        summary = f"""
════════════════════════════════════════════════════════════════
                    SONARQUBE CI ANALYSIS SUMMARY
════════════════════════════════════════════════════════════════
📅 Analysis Time: {timestamp}
🏗️  Project: {self.project_key}
🎯 Quality Gate: {qg_status} {'✅' if qg_status == 'OK' else '❌'}

📊 CODE METRICS:
├── Lines of Code: {measures.get('ncloc', 'N/A')}
├── Coverage: {measures.get('coverage', 'N/A')}%
├── Duplicated Lines: {measures.get('duplicated_lines_density', 'N/A')}%
└── Quality Ratings:
    ├── Maintainability: {self._format_rating(measures.get('sqale_rating'))}
    ├── Reliability: {self._format_rating(measures.get('reliability_rating'))}
    └── Security: {self._format_rating(measures.get('security_rating'))}

🚨 ISSUES SUMMARY:
├── Total Issues: {issue_analysis.get('total', 0)}
├── New Issues: {issue_analysis.get('new_issues', 0)}
├── By Quality Category:
│   ├── 🔧 RELIABILITY: {issue_analysis['by_category'].get('RELIABILITY', 0)} (bugs, crashes, exceptions)
│   ├── 🔒 SECURITY: {issue_analysis['by_category'].get('SECURITY', 0)} (vulnerabilities, secrets)
│   └── 🧹 MAINTAINABILITY: {issue_analysis['by_category'].get('MAINTAINABILITY', 0)} (code smells, complexity)
├── By Severity:
│   ├── 🔴 BLOCKER: {issue_analysis['by_severity'].get('BLOCKER', 0)}
│   ├── 🟠 CRITICAL: {issue_analysis['by_severity'].get('CRITICAL', 0)}
│   ├── 🟡 MAJOR: {issue_analysis['by_severity'].get('MAJOR', 0)}
│   ├── 🔵 MINOR: {issue_analysis['by_severity'].get('MINOR', 0)}
│   └── ⚪ INFO: {issue_analysis['by_severity'].get('INFO', 0)}
└── By Type:
    ├── 🐛 BUGS: {issue_analysis['by_type'].get('BUG', 0)}
    ├── 🔒 VULNERABILITIES: {issue_analysis['by_type'].get('VULNERABILITY', 0)}
    └── 💨 CODE SMELLS: {issue_analysis['by_type'].get('CODE_SMELL', 0)}
"""

        # Add critical issues if any
        critical_issues = issue_analysis.get('critical_issues', [])
        if critical_issues:
            summary += "\n⚠️  CRITICAL ISSUES (BLOCKING):\n"
            for i, issue in enumerate(critical_issues[:5], 1):  # Show max 5
                summary += f"  {i}. [{issue['severity']}] {issue['file']}:{issue.get('line', '?')}\n"
                summary += f"     {issue['message']}\n"
            
            if len(critical_issues) > 5:
                summary += f"     ... and {len(critical_issues) - 5} more critical issues\n"

        # CI Decision
        summary += "\n🎯 CI/CD DECISION:\n"
        blocker_count = issue_analysis['by_severity'].get('BLOCKER', 0)
        critical_count = issue_analysis['by_severity'].get('CRITICAL', 0)
        
        if qg_status != 'OK':
            summary += "❌ FAIL - Quality Gate failed\n"
        elif blocker_count > 0:
            summary += f"❌ FAIL - {blocker_count} BLOCKER issue(s) found\n"
        elif critical_count > 0:
            summary += f"⚠️  WARNING - {critical_count} CRITICAL issue(s) found\n"
        else:
            summary += "✅ PASS - No blocking issues found\n"

        summary += f"\n🌐 Dashboard: {self.server_url}/dashboard?id={self.project_key}"
        summary += "\n" + "═" * 64

        return summary

    def _generate_json_summary(self, metrics_data: Dict, issue_analysis: Dict, quality_gate: Dict) -> str:
        """Generate JSON summary for programmatic use"""
        measures = {}
        if metrics_data and "component" in metrics_data:
            for measure in metrics_data["component"].get("measures", []):
                measures[measure["metric"]] = measure.get("value", "N/A")

        qg_status = "UNKNOWN"
        if quality_gate and "projectStatus" in quality_gate:
            qg_status = quality_gate["projectStatus"].get("status", "UNKNOWN")

        summary = {
            "timestamp": datetime.now().isoformat(),
            "project": self.project_key,
            "quality_gate": {
                "status": qg_status,
                "passed": qg_status == "OK"
            },
            "metrics": measures,
            "issues": issue_analysis,
            "ci_decision": {
                "should_fail": qg_status != "OK" or issue_analysis['by_severity'].get('BLOCKER', 0) > 0,
                "has_warnings": issue_analysis['by_severity'].get('CRITICAL', 0) > 0,
                "is_passing": qg_status == "OK" and issue_analysis['by_severity'].get('BLOCKER', 0) == 0
            },
            "dashboard_url": f"{self.server_url}/dashboard?id={self.project_key}"
        }
        
        return json.dumps(summary, indent=2)

    def _generate_markdown_summary(self, metrics_data: Dict, issue_analysis: Dict, quality_gate: Dict) -> str:
        """Generate Markdown summary for GitHub/GitLab comments"""
        measures = {}
        if metrics_data and "component" in metrics_data:
            for measure in metrics_data["component"].get("measures", []):
                measures[measure["metric"]] = measure.get("value", "N/A")

        qg_status = "UNKNOWN"
        if quality_gate and "projectStatus" in quality_gate:
            qg_status = quality_gate["projectStatus"].get("status", "UNKNOWN")

        status_emoji = "✅" if qg_status == "OK" else "❌"
        
        summary = f"""## 🔍 SonarQube Analysis Report

### Quality Gate: {status_emoji} {qg_status}

| Metric | Value |
|--------|--------|
| Lines of Code | {measures.get('ncloc', 'N/A')} |
| Coverage | {measures.get('coverage', 'N/A')}% |
| Duplicated Lines | {measures.get('duplicated_lines_density', 'N/A')}% |
| Maintainability | {self._format_rating(measures.get('sqale_rating'))} |
| Reliability | {self._format_rating(measures.get('reliability_rating'))} |
| Security | {self._format_rating(measures.get('security_rating'))} |

### Issues Summary

| Severity | Count |
|----------|-------|
| 🔴 Blocker | {issue_analysis['by_severity'].get('BLOCKER', 0)} |
| 🟠 Critical | {issue_analysis['by_severity'].get('CRITICAL', 0)} |
| 🟡 Major | {issue_analysis['by_severity'].get('MAJOR', 0)} |
| 🔵 Minor | {issue_analysis['by_severity'].get('MINOR', 0)} |
| ⚪ Info | {issue_analysis['by_severity'].get('INFO', 0)} |

**Total Issues:** {issue_analysis.get('total', 0)} | **New Issues:** {issue_analysis.get('new_issues', 0)}

[📊 View Full Report]({self.server_url}/dashboard?id={self.project_key})
"""
        return summary

    def _get_issue_category(self, issue_type: str, rule_key: str, message: str) -> str:
        """Categorize issues by quality dimension"""
        # Direct mapping by issue type
        if issue_type == "BUG":
            return "RELIABILITY"
        elif issue_type == "VULNERABILITY":
            return "SECURITY"
        elif issue_type == "CODE_SMELL":
            return "MAINTAINABILITY"
        
        # Additional categorization by rule patterns for edge cases
        rule_lower = rule_key.lower()
        message_lower = message.lower()
        
        # Security-related patterns
        security_patterns = ["hardcoded", "password", "secret", "credential", "token", "key"]
        if any(pattern in rule_lower or pattern in message_lower for pattern in security_patterns):
            return "SECURITY"
        
        # Reliability patterns (bugs, null pointers, exceptions)
        reliability_patterns = ["null", "npe", "exception", "crash", "fail"]
        if any(pattern in rule_lower or pattern in message_lower for pattern in reliability_patterns):
            return "RELIABILITY"
        
        # Default to maintainability for code smells
        return "MAINTAINABILITY"

    def _format_rating(self, rating: str) -> str:
        """Format quality rating with emoji"""
        if not rating:
            return "N/A"
        ratings = {"1.0": "A ⭐", "2.0": "B 🟢", "3.0": "C 🟡", "4.0": "D 🟠", "5.0": "E 🔴"}
        return ratings.get(rating, rating)

    def get_exit_code(self) -> int:
        """Get appropriate exit code for CI/CD"""
        quality_gate = self.get_quality_gate_status()
        issues_data = self.get_issues()
        
        if not quality_gate or not issues_data:
            return 2  # Error in analysis
        
        qg_status = quality_gate.get("projectStatus", {}).get("status", "ERROR")
        if qg_status != "OK":
            return 1  # Quality gate failed
        
        issue_analysis = self.analyze_issues(issues_data)
        blocker_count = issue_analysis['by_severity'].get('BLOCKER', 0)
        
        if blocker_count > 0:
            return 1  # Blocker issues found
        
        return 0  # Success

def main():
    parser = argparse.ArgumentParser(description="SonarQube CI/CD Analysis Summary Generator")
    parser.add_argument("--server-url", default="http://localhost:9999", help="SonarQube server URL")
    parser.add_argument("--token", required=True, help="SonarQube authentication token")
    parser.add_argument("--project-key", required=True, help="SonarQube project key")
    parser.add_argument("--format", choices=["text", "json", "markdown"], default="text", help="Output format")
    parser.add_argument("--output", help="Output file (default: stdout)")
    parser.add_argument("--exit-code", action="store_true", help="Set exit code based on analysis results")
    parser.add_argument("--wait-for-analysis", type=int, default=0, help="Wait N seconds for analysis to complete")
    
    args = parser.parse_args()
    
    # Wait for analysis if requested
    if args.wait_for_analysis > 0:
        print(f"⏳ Waiting {args.wait_for_analysis} seconds for analysis to complete...", file=sys.stderr)
        time.sleep(args.wait_for_analysis)
    
    # Generate summary
    analyzer = SonarQubeCIAnalyzer(args.server_url, args.token, args.project_key)
    summary = analyzer.generate_ci_summary(args.format)
    
    # Output
    if args.output:
        with open(args.output, 'w') as f:
            f.write(summary)
        print(f"✅ Summary written to {args.output}", file=sys.stderr)
    else:
        print(summary)
    
    # Set exit code if requested
    if args.exit_code:
        exit_code = analyzer.get_exit_code()
        print(f"🎯 Exit code: {exit_code}", file=sys.stderr)
        sys.exit(exit_code)

if __name__ == "__main__":
    main()