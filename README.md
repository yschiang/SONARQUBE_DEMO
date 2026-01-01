# SonarQube Demo Project

A demonstration project showcasing SonarQube code quality analysis with realistic examples of common coding issues.

## 📁 Project Structure

```
sonarqube-demo/
├── src/main/java/com/example/demo/     # Java source code with intentional issues
│   ├── FooBarController.java           # REST controller with null pointer bugs
│   ├── DemoService.java               # Service layer with NPE vulnerabilities  
│   ├── DateFormatDemo.java            # YYYY date formatting bug examples
│   └── FooBarApplication.java         # Spring Boot main application
├── reports/                           # Analysis reports (generated)
├── ci-sonar-analyzer.py              # Python CI integration tool
├── ci-sonar-analyzer.sh              # Shell CI integration tool
├── ci-examples.yml                   # CI/CD configuration examples
├── pom.xml                          # Maven build configuration
└── sonar-project.properties         # SonarQube analysis configuration
```

## 🐛 Demonstrated Issues

### Null Pointer Exceptions (RELIABILITY)
- **FooBarController.java**: 6 NPE vulnerabilities in REST endpoints
- **DemoService.java**: 5 NPE vulnerabilities in business logic

### Date Formatting Bugs (RELIABILITY)  
- **DateFormatDemo.java**: 7 YYYY pattern bugs causing wrong years around Dec 31/Jan 1

### Code Quality Issues (MAINTAINABILITY)
- Hardcoded credentials and secrets
- Code complexity and duplication

## 🛠️ Usage

### Run SonarQube Analysis
```bash
# Start SonarQube
docker run -d --name sonarqube -p 9999:9000 sonarqube:latest

# Run analysis
mvn clean compile sonar:sonar -Dsonar.host.url=http://localhost:9999
```

### Generate CI Reports
```bash
# Python version (recommended)
python3 ci-sonar-analyzer.py --token <token> --project-key sonarqube-demo

# Shell version  
./ci-sonar-analyzer.sh --token <token> --project-key sonarqube-demo
```

## 📊 Analysis Results

The project contains **30 total issues**:
- **🔧 RELIABILITY: 21 issues** (bugs, crashes, exceptions)
- **🔒 SECURITY: 0 issues** (vulnerabilities, secrets)  
- **🧹 MAINTAINABILITY: 9 issues** (code smells, complexity)

## 🎯 CI/CD Integration

See `ci-examples.yml` for ready-to-use configurations for:
- GitHub Actions
- GitLab CI
- Jenkins
- Azure DevOps
- CircleCI

## 📈 Quality Metrics

- **Lines of Code**: 301
- **Reliability Rating**: C 🟡 (due to bugs)
- **Security Rating**: A ⭐
- **Maintainability Rating**: A ⭐