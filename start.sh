#!/bin/bash

# G6Portal Application Startup Script
# This script sets up the environment and starts the G6Portal application

echo "Starting G6Portal Application..."
echo "=================================="

# Initialize SDKMAN if available
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    echo "Loading SDKMAN..."
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    
    # Use Java 11 for compatibility
    echo "Setting Java 11..."
    sdk use java 11.0.24-tem
else
    echo "Warning: SDKMAN not found. Make sure Java 11 is installed and set as JAVA_HOME"
fi

# Set JAVA_HOME explicitly
export JAVA_HOME=~/.sdkman/candidates/java/current

# Check Java version
echo "Java version:"
java -version

echo ""
echo "Starting application on http://localhost:9090/"
echo "Press Ctrl+C to stop the application"
echo "=================================="

# Start the application
./gradlew bootRun --no-daemon

echo ""
echo "Application stopped."