#!/bin/bash
# JAScanner Security Audit Script
# This script scans the codebase for common security issues

set -e

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

ISSUES_FOUND=0

echo "🔍 Starting JAScanner Security Audit..."
echo ""

# Function to check and report findings
check_pattern() {
local description="$1"
local pattern="$2"
local file_types="$3"
local severity="$4" # ERROR or WARNING

echo "Checking: $description"

if grep -r -n "$pattern" . $file_types \
--exclude-dir={.git,.gradle,build,.idea,node_modules} \
--exclude="*.{md,txt,json,xml,pro}" 2>/dev/null; then

if [ "$severity" = "ERROR" ]; then
echo -e "${RED}❌ CRITICAL: $description found!${NC}"
ISSUES_FOUND=$((ISSUES_FOUND + 1))
else
echo -e "${YELLOW}⚠️ WARNING: $description found${NC}"
fi
else
echo -e "${GREEN}✅ No issues found${NC}"
fi
echo ""
}

# 1. Check for hardcoded API keys
check_pattern "Hardcoded API keys" \
'api[_-]?key["'\'''\s]*[=:][\s]*["'\''""][^"'\''""]+["'\''""]' \
"--include=*.kt --include=*.java" \
"ERROR"

# 2. Check for hardcoded passwords
check_pattern "Hardcoded passwords" \
'password["'\'''\s]*[=:][\s]*["'\''""][^"'\''""]+["'\''""]' \
"--include=*.kt --include=*.java" \
"ERROR"

# 3. Check for hardcoded secrets
check_pattern "Hardcoded secrets/tokens" \
'[secret|token]["'\'''\s]*[=:][\s]*["'\''""][^"'\''""]+["'\''""]' \
"--include=*.kt --include=*.java" \
"ERROR"

# 4. Check for HTTP URLs (should use HTTPS)
check_pattern "HTTP URLs (should use HTTPS)" \
'http://' \
"--include=*.kt --include=*.java" \
"WARNING"

# 5. Check for SQL injection vulnerabilities
check_pattern "Potential SQL injection (rawQuery)" \
'rawQuery.*".*\$' \
"--include=*.kt --include=*.java" \
"ERROR"

# 6. Check for weak cryptographic algorithms
check_pattern "Weak crypto: MD5" \
'MessageDigest\.getInstance.*"MD5"' \
"--include=*.kt --include=*.java" \
"ERROR"

check_pattern "Weak crypto: SHA1" \
'MessageDigest\.getInstance.*"SHA-1"' \
"--include=*.kt --include=*.java" \
"WARNING"

# 7. Check for insecure random number generation
check_pattern "Insecure Random (use SecureRandom)" \
'new Random\(' \
"--include=*.kt --include=*.java" \
"WARNING"

# 8. Check for debuggable in manifest
check_pattern "Debuggable flag in AndroidManifest" \
'android:debuggable="true"' \
"--include=AndroidManifest.xml" \
"ERROR"

# 9. Check for allowBackup=true
check_pattern "Allow backup enabled" \
'android:allowBackup="true"' \
"--include=AndroidManifest.xml" \
"WARNING"

# 10. Check for cleartext traffic
check_pattern "Cleartext traffic allowed" \
'android:usesCleartextTraffic="true"' \
"--include=AndroidManifest.xml" \
"ERROR"

# 11. Check for exported components without permission
check_pattern "Exported components without permissions" \
'android:exported="true"[^>]*>' \
"--include=AndroidManifest.xml" \
"WARNING"

# 12. Check for WebView JavaScript enabled without validation
check_pattern "WebView JavaScript enabled" \
'setJavaScriptEnabled\(true\)' \
"--include=*.kt --include=*.java" \
"WARNING"

# 13. Check for file:// scheme usage
check_pattern "file:// scheme usage" \
'file://' \
"--include=*.kt --include=*.java" \
"WARNING"

# 14. Check for external storage usage
check_pattern "External storage usage (potential data leak)" \
'Environment\.getExternalStorageDirectory' \
"--include=*.kt --include=*.java" \
"WARNING"

# 15. Check for logging of sensitive data
check_pattern "Logging statements (may leak PII in production)" \
'Log\.[dviwe]\(' \
"--include=*.kt --include=*.java" \
"WARNING"

# 16. Check for TODO/FIXME related to security
echo "Checking: Security-related TODOs/FIXMEs"
if grep -r -n "TODO.*security\|FIXME.*security\|TODO.*encrypt\|FIXME.*encrypt" . \
--include="*.kt" --include="*.java" \
--exclude-dir={.git,.gradle,build} 2>/dev/null; then
echo -e "${YELLOW}⚠️ Security-related TODOs found${NC}"
else
echo -e "${GREEN}✅ No security TODOs found${NC}"
fi
echo ""

# 17. Check for private keys in repository
echo "Checking: Private keys in repository"
if find . -type f \( -name "*.pem" -o -name "*.key" -o -name "*.p12" -o -name "*.jks" \) \
-not -path "./.git/*" -not -path "./build/*" 2>/dev/null | grep -v "^$"; then
echo -e "${RED}❌ CRITICAL: Private key files found in repository!${NC}"
ISSUES_FOUND=$((ISSUES_FOUND + 1))
else
echo -e "${GREEN}✅ No private key files found${NC}"
fi
echo ""

# 18. Check gradle.properties for sensitive data
echo "Checking: gradle.properties for sensitive data"
if [ -f "gradle.properties" ]; then
if grep -E "password|secret|api[_-]?key" gradle.properties 2>/dev/null; then
echo -e "${YELLOW}⚠️ Potential secrets in gradle.properties${NC}"
else
echo -e "${GREEN}✅ No obvious secrets in gradle.properties${NC}"
fi
else
echo -e "${GREEN}✅ gradle.properties not found${NC}"
fi
echo ""

# Summary
echo "================================"
echo "Security Audit Summary"
echo "================================"

if [ $ISSUES_FOUND -gt 0 ]; then
echo -e "${RED}❌ Found $ISSUES_FOUND critical security issues${NC}"
echo "Please review and fix the issues above before release."
exit 1
else
echo -e "${GREEN}✅ No critical security issues found${NC}"
echo "Note: Some warnings may still require review."
exit 0
fi
