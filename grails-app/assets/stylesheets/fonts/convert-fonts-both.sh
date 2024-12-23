#!/bin/bash

# Check for required tools
check_requirements() {
    local missing_tools=()

    if ! command -v woff2_compress &> /dev/null; then
        missing_tools+=("woff2_compress (package: woff2)")
    fi
    
    if ! command -v sfnt2woff &> /dev/null; then
        missing_tools+=("sfnt2woff (package: woff-tools)")
    fi

    if [ ${#missing_tools[@]} -ne 0 ]; then
        echo "Error: Missing required tools:"
        for tool in "${missing_tools[@]}"; do
            echo "  - $tool"
        done
        echo
        echo "Please install them using:"
        echo "  Ubuntu/Debian: sudo apt-get install woff2 woff-tools"
        echo "  Mac (Homebrew): brew install woff2 && brew install woff-tools"
        exit 1
    fi
}

# Default to current directory if no argument provided
FONT_DIR="${1:-.}"

# Check requirements first
check_requirements

# Check if directory exists
if [ ! -d "$FONT_DIR" ]; then
    echo "Error: Directory '$FONT_DIR' not found!"
    exit 1
fi

# Navigate to font directory
cd "$FONT_DIR" || exit 1

# Count total TTF files
total_files=$(ls -1 *.ttf 2>/dev/null | wc -l)

if [ "$total_files" -eq 0 ]; then
    echo "No TTF files found in $FONT_DIR"
    exit 1
fi

echo "Found $total_files TTF files to convert"
echo "----------------------------------------"

# Initialize counters
current=0
success_woff2=0
failed_woff2=0
success_woff=0
failed_woff=0

# Process each TTF file
for file in *.ttf; do
    if [ -f "$file" ]; then
        ((current++))
        base_name="${file%.ttf}"
        
        echo "[$current/$total_files] Processing $file..."
        
        # Convert to WOFF2
        echo -n "  Converting to WOFF2... "
        if woff2_compress "$file" 2>/dev/null; then
            echo "✓ Done"
            ((success_woff2++))
        else
            echo "✗ Failed"
            ((failed_woff2++))
        fi
        
        # Convert to WOFF
        echo -n "  Converting to WOFF... "
        if sfnt2woff "$file" 2>/dev/null; then
            echo "✓ Done"
            ((success_woff++))
        else
            echo "✗ Failed"
            ((failed_woff++))
        fi
        
        echo
    fi
done

echo "----------------------------------------"
echo "Conversion complete!"
echo
echo "WOFF2 conversions:"
echo "  Successfully converted: $success_woff2"
echo "  Failed conversions: $failed_woff2"
echo
echo "WOFF conversions:"
echo "  Successfully converted: $success_woff"
echo "  Failed conversions: $failed_woff"
echo
echo "Total files processed: $total_files"

# If there were any failures, exit with error code
[ $((failed_woff2 + failed_woff)) -gt 0 ] && exit 1 || exit 0
