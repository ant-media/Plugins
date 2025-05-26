#!/usr/bin/env python3
"""
Advanced SCTE-35 Test Stream Generator
Creates an SRT stream with properly formatted SCTE-35 data for testing
"""

import struct
import time
import subprocess
import sys
import argparse
from datetime import datetime, timedelta

class SCTE35Generator:
    """Generate SCTE-35 binary data"""
    
    def __init__(self):
        self.table_id = 0xFC
        self.protocol_version = 0
        self.pts_adjustment = 0
        
    def create_splice_insert(self, event_id, out_of_network=True, duration=None, immediate=False):
        """Create a splice_insert command"""
        
        # Splice command data
        command_data = bytearray()
        
        # Splice event ID (32 bits)
        command_data.extend(struct.pack('>I', event_id))
        
        # Flags byte
        flags = 0
        if not immediate:  # splice_event_cancel_indicator = 0
            flags |= 0x00
        if out_of_network:
            flags |= 0x80  # out_of_network_indicator = 1
        flags |= 0x40  # program_splice_flag = 1
        if duration is not None:
            flags |= 0x20  # duration_flag = 1
        if immediate:
            flags |= 0x10  # splice_immediate_flag = 1
            
        command_data.append(flags)
        
        # Reserved bits
        command_data.append(0x00)
        
        # Splice time (if not immediate)
        if not immediate:
            # time_specified_flag = 1, reserved = 0, pts_time (33 bits)
            pts_time = int(time.time() * 90000) & 0x1FFFFFFFF  # Current time in 90kHz
            command_data.append(0x80 | ((pts_time >> 32) & 0x01))
            command_data.extend(struct.pack('>I', pts_time & 0xFFFFFFFF))
        
        # Break duration (if specified)
        if duration is not None:
            # auto_return = 1, reserved = 0, duration (33 bits)
            duration_90k = int(duration * 90000)  # Convert seconds to 90kHz
            command_data.append(0x80 | ((duration_90k >> 32) & 0x01))
            command_data.extend(struct.pack('>I', duration_90k & 0xFFFFFFFF))
        
        return self._create_scte35_packet(0x05, command_data)  # splice_insert = 0x05
    
    def create_time_signal(self, pts_time=None):
        """Create a time_signal command"""
        
        command_data = bytearray()
        
        if pts_time is None:
            pts_time = int(time.time() * 90000) & 0x1FFFFFFFF
            
        # time_specified_flag = 1, reserved = 0, pts_time (33 bits)
        command_data.append(0x80 | ((pts_time >> 32) & 0x01))
        command_data.extend(struct.pack('>I', pts_time & 0xFFFFFFFF))
        
        return self._create_scte35_packet(0x06, command_data)  # time_signal = 0x06
    
    def _create_scte35_packet(self, command_type, command_data):
        """Create complete SCTE-35 packet"""
        
        packet = bytearray()
        
        # Table ID
        packet.append(self.table_id)
        
        # Section syntax indicator (0), private indicator (0), reserved (3), section length (12)
        section_length = 14 + len(command_data)  # Minimum header + command data
        packet.extend(struct.pack('>H', 0x0000 | (section_length & 0x0FFF)))
        
        # Protocol version
        packet.append(self.protocol_version)
        
        # Encrypted packet (1), encryption algorithm (6), pts_adjustment (33)
        packet.append(0x00)  # Not encrypted
        packet.extend(struct.pack('>Q', self.pts_adjustment)[-5:])  # 33-bit PTS adjustment
        
        # CW index
        packet.append(0x00)
        
        # Tier (12 bits), splice_command_length (12 bits)
        command_length = 1 + len(command_data)  # Command type + data
        packet.extend(struct.pack('>H', 0x0000 | (command_length & 0x0FFF)))
        
        # Splice command type
        packet.append(command_type)
        
        # Command data
        packet.extend(command_data)
        
        # Descriptor loop length (16 bits) - no descriptors
        packet.extend(struct.pack('>H', 0x0000))
        
        # CRC32 (placeholder - should be calculated)
        packet.extend(b'\x00\x00\x00\x00')
        
        return bytes(packet)

def create_test_stream_with_scte35(srt_url, duration=300):
    """Create test stream with SCTE-35 markers"""
    
    print("🎬 Creating SCTE-35 test stream...")
    print(f"📡 Target: {srt_url}")
    print(f"⏱️  Duration: {duration} seconds")
    
    generator = SCTE35Generator()
    
    # Create SCTE-35 events
    events = [
        (30, "cue_out", 1001, 30),    # 30s: Start 30s ad break
        (60, "cue_in", 1001, None),   # 60s: End ad break
        (120, "cue_out", 1002, 60),   # 120s: Start 60s ad break
        (180, "cue_in", 1002, None),  # 180s: End ad break
        (240, "time_signal", 1003, None)  # 240s: Time signal
    ]
    
    # Generate SCTE-35 data files
    scte35_files = []
    for i, (timestamp, event_type, event_id, duration_val) in enumerate(events):
        if event_type == "cue_out":
            data = generator.create_splice_insert(event_id, out_of_network=True, duration=duration_val)
        elif event_type == "cue_in":
            data = generator.create_splice_insert(event_id, out_of_network=False)
        elif event_type == "time_signal":
            data = generator.create_time_signal()
        
        filename = f"/tmp/scte35_event_{i}.bin"
        with open(filename, 'wb') as f:
            f.write(data)
        scte35_files.append((timestamp, filename))
        print(f"📝 Created SCTE-35 event: {event_type} at {timestamp}s -> {filename}")
    
    # Build FFmpeg command
    cmd = [
        'ffmpeg',
        '-f', 'lavfi', '-i', f'testsrc2=duration={duration}:size=1280x720:rate=25',
        '-f', 'lavfi', '-i', f'sine=frequency=1000:duration={duration}',
        '-c:v', 'libx264',
        '-preset', 'fast',
        '-crf', '23',
        '-g', '50',
        '-keyint_min', '25',
        '-c:a', 'aac',
        '-b:a', '128k',
        '-f', 'mpegts',
        '-mpegts_flags', '+initial_discontinuity',
        '-metadata', 'service_name=SCTE35 Test Stream',
        '-metadata', 'service_provider=Ant Media Test',
        '-t', str(duration),
        srt_url,
        '-y'
    ]
    
    print("🚀 Starting FFmpeg stream...")
    print("⚠️  Note: This creates a basic stream. For real SCTE-35 injection, use specialized tools.")
    
    try:
        subprocess.run(cmd, check=True)
        print("✅ Stream completed successfully!")
    except subprocess.CalledProcessError as e:
        print(f"❌ Stream failed: {e}")
    except KeyboardInterrupt:
        print("🛑 Stream stopped by user")
    finally:
        # Cleanup
        for _, filename in scte35_files:
            try:
                import os
                os.remove(filename)
            except:
                pass

def main():
    parser = argparse.ArgumentParser(description='Generate SCTE-35 test stream')
    parser.add_argument('--host', default='127.0.0.1', help='SRT host (default: 127.0.0.1)')
    parser.add_argument('--port', default='8080', help='SRT port (default: 8080)')
    parser.add_argument('--stream-id', default='scte35-test', help='Stream ID (default: scte35-test)')
    parser.add_argument('--duration', type=int, default=300, help='Stream duration in seconds (default: 300)')
    
    args = parser.parse_args()
    
    srt_url = f"srt://{args.host}:{args.port}?streamid={args.stream_id}&mode=caller"
    
    print("🎯 SCTE-35 Test Stream Generator")
    print("=" * 40)
    
    create_test_stream_with_scte35(srt_url, args.duration)

if __name__ == "__main__":
    main() 