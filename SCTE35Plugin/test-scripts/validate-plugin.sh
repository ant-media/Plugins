#!/bin/bash

# SCTE-35 Plugin Validation Script
# This script validates that the SCTE-35 plugin is working correctly

# Configuration
AMS_HOST="localhost"
AMS_PORT="5080"
AMS_APP="WebRTCAppEE"
TEST_STREAM_ID="scte35-validation-test"
SRT_PORT="8080"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results
TESTS_PASSED=0
TESTS_FAILED=0
TOTAL_TESTS=0

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   SCTE-35 Plugin Validation Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to run a test
run_test() {
    local test_name="$1"
    local test_command="$2"
    local expected_result="$3"
    
    echo -e "${YELLOW}Testing: $test_name${NC}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if eval "$test_command"; then
        echo -e "${GREEN}✓ PASSED: $test_name${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}✗ FAILED: $test_name${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Function to check if service is running
check_service() {
    local service_name="$1"
    local port="$2"
    
    if nc -z "$AMS_HOST" "$port" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

# Function to check log for pattern
check_log_pattern() {
    local pattern="$1"
    local log_file="$2"
    
    if [ -f "$log_file" ]; then
        grep -q "$pattern" "$log_file"
        return $?
    else
        # Try common log locations
        for log_path in "/opt/antmedia/log/ant-media-server.log" "/usr/local/antmedia/log/ant-media-server.log" "../../log/ant-media-server.log"; do
            if [ -f "$log_path" ]; then
                grep -q "$pattern" "$log_path"
                return $?
            fi
        done
        return 1
    fi
}

# Function to make HTTP request
make_request() {
    local url="$1"
    local expected_status="$2"
    
    local response=$(curl -s -w "%{http_code}" -o /dev/null "$url" 2>/dev/null)
    
    if [ "$response" = "$expected_status" ]; then
        return 0
    else
        return 1
    fi
}

echo -e "${YELLOW}Starting validation tests...${NC}"
echo ""

# Test 1: Check if Ant Media Server is running
run_test "Ant Media Server is running" \
         "check_service 'Ant Media Server' '$AMS_PORT'" \
         "Service should be accessible on port $AMS_PORT"

# Test 2: Check if REST API is accessible
run_test "REST API is accessible" \
         "make_request 'http://$AMS_HOST:$AMS_PORT/$AMS_APP/rest/v2/version' '200'" \
         "REST API should return 200 status"

# Test 3: Check if plugin JAR exists
run_test "Plugin JAR file exists" \
         "[ -f '../target/ant-media-scte35-plugin.jar' ] || [ -f '/opt/antmedia/plugins/ant-media-scte35-plugin.jar' ] || [ -f '/usr/local/antmedia/plugins/ant-media-scte35-plugin.jar' ]" \
         "Plugin JAR should exist in target or plugins directory"

# Test 4: Check plugin initialization in logs
run_test "Plugin initialization logged" \
         "check_log_pattern 'SCTE-35 Plugin' '/opt/antmedia/log/antmedia.log'" \
         "Plugin initialization should be logged"

# Test 5: Check if SRT port is available for testing
run_test "SRT port available for testing" \
         "! nc -z localhost $SRT_PORT" \
         "SRT port $SRT_PORT should be available for test stream"

# Test 6: Check FFmpeg availability
run_test "FFmpeg is available" \
         "command -v ffmpeg >/dev/null 2>&1" \
         "FFmpeg should be installed and in PATH"

# Test 7: Check Python availability
run_test "Python 3 is available" \
         "command -v python3 >/dev/null 2>&1" \
         "Python 3 should be installed and in PATH"

# Test 8: Test stream creation capability
if command -v ffmpeg >/dev/null 2>&1; then
    run_test "Can create test stream" \
             "timeout 5 ffmpeg -f lavfi -i testsrc2=duration=1:size=320x240:rate=25 -f null - >/dev/null 2>&1" \
             "FFmpeg should be able to create test video"
fi

# Test 9: Check if we can access broadcast list
run_test "Can access broadcast list" \
         "make_request 'http://$AMS_HOST:$AMS_PORT/$AMS_APP/rest/v2/broadcasts/list/0/10' '200'" \
         "Should be able to access broadcast list via REST API"

# Test 10: Check basic REST API functionality
run_test "Basic REST API accessible" \
         "make_request 'http://$AMS_HOST:$AMS_PORT/$AMS_APP/rest/v2/broadcasts/count' '200'" \
         "Basic REST API endpoints should be accessible"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}           Validation Results${NC}"
echo -e "${BLUE}========================================${NC}"

echo -e "Total Tests: $TOTAL_TESTS"
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"

if [ $TESTS_FAILED -eq 0 ]; then
    echo ""
    echo -e "${GREEN}🎉 All tests passed! The SCTE-35 plugin appears to be ready for testing.${NC}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo -e "1. Run a test stream: ./test-scripts/create-scte35-test-stream.sh"
    echo -e "2. Monitor logs: tail -f /opt/antmedia/log/antmedia.log | grep -i scte35"
    echo -e "3. Check HLS output: curl http://$AMS_HOST:$AMS_PORT/$AMS_APP/streams/your-stream.m3u8"
    echo ""
    exit 0
else
    echo ""
    echo -e "${RED}❌ Some tests failed. Please check the issues above before proceeding.${NC}"
    echo ""
    echo -e "${YELLOW}Common solutions:${NC}"
    echo -e "• Make sure Ant Media Server is running"
    echo -e "• Verify the plugin JAR is in the plugins directory"
    echo -e "• Check server logs for error messages"
    echo -e "• Ensure FFmpeg and Python 3 are installed"
    echo ""
    exit 1
fi 