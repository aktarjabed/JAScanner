#!/bin/bash
set -e

echo "🔧 Fixing JAScanner repository..."

# 1. Remove build artifacts from tracking
echo "1. Removing build artifacts..."
git rm -r --cached .gradle/ build/ */build/ app/build/ 2>/dev/null || true

# 2. Update .gitignore
echo "2. Updating .gitignore..."
cat >> .gitignore << 'EOF'

# Build artifacts
.gradle/
build/
*/build/
**/build/
EOF

# 3. Add R8 line to gradle.properties
echo "3. Adding R8 configuration..."
if ! grep -q "android.enableR8.fullMode=true" gradle.properties; then
    echo "" >> gradle.properties
    echo "android.enableR8.fullMode=true" >> gradle.properties
fi

# 4. Stage only correct files
echo "4. Staging files..."
git add .gitignore
git add gradle.properties
git add .github/workflows/android.yml 2>/dev/null || echo "Skipping android.yml (not found)"
git add app/proguard-rules.pro 2>/dev/null || echo "Skipping proguard-rules.pro (not found)"
git add scripts/scan_secrets.sh 2>/dev/null || echo "Skipping scan_secrets.sh (not found)"
git add app/build.gradle.kts 2>/dev/null || echo "Skipping build.gradle.kts (not modified)"

# 5. Verify
echo "5. Verifying..."
echo "=== Files to be committed: ==="
git status --short
echo "=============================="

# Check for R8 line
if grep -q "android.enableR8.fullMode=true" gradle.properties; then
    echo "✅ R8 line verified in gradle.properties"
else
    echo "❌ ERROR: R8 line missing!"
    exit 1
fi

# Check for build artifacts
if git diff --cached --name-only | grep -E "\.gradle/|/build/"; then
    echo "❌ ERROR: Build artifacts in staged changes!"
    exit 1
else
    echo "✅ No build artifacts in staged changes"
fi

echo ""
echo "✅ Ready to commit! Run:"
echo "git commit -m 'Add CI/CD, ProGuard, and security hardening'"
echo "git push origin main"
