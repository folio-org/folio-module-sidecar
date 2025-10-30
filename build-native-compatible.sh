#!/bin/bash

# Script to rebuild the native application with GLIBC compatibility

echo "🔨 Building native application with compatibility fixes..."

# Function to show available options
show_options() {
    echo ""
    echo "📦 Available Docker build options:"
    echo "   1. Multi-stage with UBI tools:     docker build -f docker/Dockerfile.native-micro -t folio-module-sidecar-native ."
    echo "   2. Simple runtime-only:            docker build -f docker/Dockerfile.native-micro-simple -t folio-module-sidecar-native ."
    echo "   3. Official Quarkus builder:       docker build -f docker/Dockerfile.native-quarkus -t folio-module-sidecar-native ."
    echo ""
    echo "💡 Recommendation: Try option 3 first (Quarkus builder), then option 1 if you need more control."
}

# Check command line argument
if [[ "$1" == "--docker-only" ]]; then
    echo "🐳 Skipping Maven build, showing Docker options only..."
    show_options
    exit 0
fi

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./mvnw clean

# Build native application with compatible settings
echo "🏗️  Building native executable..."
./mvnw package -Dnative -DskipTests

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "✅ Native build completed successfully!"
    show_options
else
    echo "❌ Native build failed!"
    echo "🐳 You can still try the multi-stage Docker builds that don't require pre-built binaries:"
    echo "   docker build -f docker/Dockerfile.native-micro -t folio-module-sidecar-native ."
    echo "   docker build -f docker/Dockerfile.native-quarkus -t folio-module-sidecar-native ."
    exit 1
fi