#include "../interface/zixi_feeder_interface.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#ifdef WIN32
#include <windows.h>
#include <WinSock2.h>
#else
#include <time.h>
#include <sys/time.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#endif
#include <errno.h>

unsigned long getTickCount()
{
#ifndef WIN32
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return (unsigned long)(tv.tv_sec * 1000 + tv.tv_usec / 1000);
#else
	return GetTickCount();
#endif
}

void encoder_feedback(int bps, bool iframe, void* data)
{
	printf("encoder_feedback: set %d\n", bps);
}


enum
{
 PARAM_INPORT = 1,
 PARAM_HOST,
 PARAM_PORT,
 PARAM_CHANNEL,
 PARAM_LATENCY,
 PARAM_BITRATE_KBPS,
 PARAM_ENFORCE_BITRATE,
 PARAM_LOGLEVEL,
 PARAM_IGNORE_DTLS_ERROR,
 MANDATORY_PARAM_COUNT
};


#define AUTO_BONDING	"auto"

#define MAX_BONDING_NICS	5

void sleep_ms(long miliSecs)
{
#ifndef WIN32
	struct timespec timeOut;
	timeOut.tv_sec = miliSecs/1000;
	timeOut.tv_nsec = (miliSecs%1000) * 1000000;
	nanosleep(&timeOut, NULL);
#else
	Sleep(miliSecs);
#endif
}

void log_func(void *ud, int l, const char *msg)
{
	printf(msg);
}

void deleter(char*p, int size, void*param)
{
	free(p);
}


int main(int argc, char *argv[])
{
	int log_level, kbps;
	int ret;
	zixi_stream_config cfg = {0};
	ZIXI_NETWORK_STATS net_stats = {0};
	ZIXI_ERROR_CORRECTION_STATS error_correction_stats = {0};
	ZIXI_CONNECTION_STATS conn_stats = {0};

	void *stream_handler = NULL;
	char tmp[1500] = {0};
	unsigned long start, stats_time, nics_auto_update_time;
	unsigned long long BytesSent = 0;
	int bytes_read = 0;
	//FILE* src_file;
	bool adjustable = false;
	struct sockaddr_in si_me, si_other;
	int s;
	unsigned int slen=sizeof(si_other);
	int in_port;
	int indx;
	int buffsize = 1000000;
	unsigned int buf_len = sizeof(int);
	unsigned long long packets_rejected = 0;

	if (argc < MANDATORY_PARAM_COUNT)
	{
		printf("Usage: feeder_interface_test <in_port> <out_ip> <out_port> <channel> <latency_ms> <speed_kbps> <enforce_bitrate> <log_level> <ignore_dtls_error> [ auto [<update_interval_ms>] | <nic> <nic> <nic> ...] \n");
		return 1;
	}


	if (argc > MAX_BONDING_NICS + MANDATORY_PARAM_COUNT )
	{
		printf("Usage: feeder_interface_test <in_port> <out_ip> <out_port> <channel> <latency_ms> <speed_kbps> <enforce_bitrate> <log_level> <ignore_dtls_error> [ auto [<update_interval_ms>] |  <nic> <nic> <nic> ... (up to %d nics are supported)] \n", MAX_BONDING_NICS);
		return 2;
	}


	in_port = atoi(argv[PARAM_INPORT]);
	
	if ((s=socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP))==-1)
	{
		getchar();
		return -1;
	}

	if (setsockopt(s, SOL_SOCKET, SO_RCVBUF, (char*)&buffsize, sizeof(int)) == -1) 
	{
		//fprintf(stderr, "Error setting receive buffer size: %s\n", strerror(errno));
		getchar();
		return -1;
	}


	if (getsockopt(s, SOL_SOCKET, SO_RCVBUF, (char*)&buffsize, &buf_len) == -1) 
	{
		//fprintf(stderr, "Error getting receive buffer size: %s\n", strerror(errno));
		getchar();
		return -1;
	}

	printf("Receive buffer=%d\n", buffsize);
	
	memset((char *) &si_me, 0, sizeof(si_me));
	si_me.sin_family = AF_INET;
	si_me.sin_port = htons(in_port);
	si_me.sin_addr.s_addr = htonl(INADDR_ANY);
	if (bind(s, (struct sockaddr*)&si_me, sizeof(si_me))==-1)
	{
		printf("Listening to port %d failed\n", in_port);
		getchar();
		return -1;
	}

	//src_file = fopen(argv[PARAM_FILENAME], "rb");
	//if (!src_file)
	//{
	//	printf("Failed openning file %s for reading\n",  argv[PARAM_FILENAME]);
	//	return 1;
	//}
	
	

	log_level = atoi(argv[PARAM_LOGLEVEL]);
	ret = zixi_configure_logging((ZIXI_LOG_LEVELS)log_level, log_func, NULL);
	
	if(ret != ZIXI_ERROR_OK)
	{
		printf("Failed to configure logging. Error: %d\n", ret);
		getchar();
		return 1;
	}

	ret = zixi_configure_credentials("test_user", strlen("test_user"), "", 0);
	if(ret != ZIXI_ERROR_OK)
	{
		printf("Failed to configure credentials. Error: %d\n", ret);
		getchar();
		return 1;
	}

	printf("Starting test\n");
	kbps = atoi(argv[PARAM_BITRATE_KBPS]);

	adjustable = false;

	cfg.enc_type = ZIXI_NO_ENCRYPTION;
	cfg.sz_enc_key = NULL;
	cfg.fast_connect = 0;
	cfg.max_bitrate = kbps * 1000;
	cfg.max_latency_ms = atoi(argv[PARAM_LATENCY]);
	cfg.port = (unsigned short*) malloc( 1 * sizeof(unsigned short));
	cfg.port[0] = atoi(argv[PARAM_PORT]);
	cfg.sz_stream_id = argv[PARAM_CHANNEL]; 
	cfg.stream_id_max_length = strlen(cfg.sz_stream_id);
	cfg.sz_hosts = (char**) malloc ( 1* sizeof(char*));
	cfg.hosts_len = (int*) malloc (1 * sizeof(int));
	cfg.sz_hosts[0] = argv[PARAM_HOST]; // broadcaster address
	cfg.hosts_len[0] = strlen(cfg.sz_hosts[0]);
	cfg.reconnect = 1;
	cfg.num_hosts = 1;
	cfg.use_compression = 1;
	cfg.rtp = 0;
	cfg.fec_overhead = 15;
	cfg.content_aware_fec = false;
	cfg.fec_block_ms = 30;
	cfg.timeout = 0;
	cfg.limited = (ZIXI_ADAPTIVE_MODE)ZIXI_ADAPTIVE_FEC;
	cfg.smoothing_latency = 0;
	cfg.enforce_bitrate = atoi(argv[PARAM_ENFORCE_BITRATE]);
	cfg.force_bonding = 0;

	cfg.protocol = ZIXI_PROTOCOL_UDP;


	int auto_nics_update_interval = -1;
	if (argc > MANDATORY_PARAM_COUNT && strncmp(argv[MANDATORY_PARAM_COUNT], AUTO_BONDING, 4) == 0)
	{
		cfg.num_local_nics = 0;
		cfg.force_bonding = 1;
		cfg.local_nics = 0;

		if (argc > MANDATORY_PARAM_COUNT + 1)
		{
			auto_nics_update_interval = atoi(argv[MANDATORY_PARAM_COUNT + 1]);
			if (auto_nics_update_interval == 0)
			{
				printf("Failed to parse auto bonding update interval: %s\n", argv[MANDATORY_PARAM_COUNT + 1]);
				getchar();
				return -2;
			}
		}

	}
	else
	{
		cfg.num_local_nics = argc - MANDATORY_PARAM_COUNT;

		if (cfg.num_local_nics > 0)
		{
			cfg.local_nics = (zixi_nic_config*)malloc(sizeof(zixi_nic_config) * cfg.num_local_nics);
			memset(cfg.local_nics, 0, sizeof(zixi_nic_config) * cfg.num_local_nics);
		}
		else
			cfg.local_nics = 0;

		for (indx = 0; indx < cfg.num_local_nics; ++indx)
		{
			cfg.local_nics[indx].nic_ip = argv[MANDATORY_PARAM_COUNT + indx];
			cfg.local_nics[indx].backup = indx % 2;
			cfg.local_nics[indx].bitrate_limit = cfg.max_bitrate;
			cfg.local_nics[indx].device = 0;
			cfg.local_nics[indx].local_port = 0;
		}
	}
	

	cfg.ignore_dtls_cert_error = atoi(argv[PARAM_IGNORE_DTLS_ERROR]);

	if (cfg.limited== ZIXI_ADAPTIVE_ENCODER)
	{
		encoder_control_info enc;
		enc.max_bitrate = kbps *8/10 * 1000;
		enc.min_bitrate = 200 * 1000;
		enc.aggressiveness = 10;
		enc.update_interval = 0;
		enc.setter = encoder_feedback;
		cfg.force_padding = true;

		ret = zixi_open_stream(cfg, &enc, &stream_handler);
	}
	else
	{
		ret = zixi_open_stream(cfg, 0, &stream_handler);
	}

	if(ret != ZIXI_ERROR_OK)
	{
		printf("Failed to add stream. Error: %d\n", ret );
		getchar();
		return 1;
	}
	printf("Stream created\n");


	nics_auto_update_time = stats_time = start = getTickCount();
	
	
	do
	{
		unsigned long now;
		bytes_read = recvfrom(s, tmp, 1500, 0,(struct sockaddr*) &si_other, &slen);
		if (bytes_read <= 0)
		{
			//printf("recvfrom failed. read=%d, err=%d\n", bytes_read, errno);
			bytes_read = 100;
			continue;
		}

		now = getTickCount();
		ret = zixi_send_frame(stream_handler, tmp, bytes_read, (cfg.rtp || cfg.smoothing_latency) ? 0: now * 90);
		if (ret == ZIXI_ERROR_NOT_READY)
		{
			++packets_rejected;
			printf("packet too fast. rejected=%llu\n", packets_rejected);
		}

		BytesSent += bytes_read;

		if (now - stats_time > 1000)
		{
			stats_time = getTickCount();
			ret = zixi_get_stats(stream_handler, &conn_stats, &net_stats, &error_correction_stats);
			if(ret != ZIXI_ERROR_OK)
				printf("Failed to get statistics.\n");
			else
				printf("Stats:\n"
					   "===================================\n"
					   "Bitrate:\t%u\nTotal packets:\t%llu\nLost:\t%llu\n"
					   "===================================\n"
					  ,net_stats.bit_rate, net_stats.packets, error_correction_stats.not_recovered);
		}

		if ((auto_nics_update_interval > 0) && (now - nics_auto_update_time > auto_nics_update_interval) )
		{
			nics_auto_update_time = now;
			ret = zixi_set_automatic_ips(stream_handler);
			if (ret != ZIXI_ERROR_OK)
			{
				printf("Failed to update local NICs. ret: %d\n", ret);
			}
		}

	} while (bytes_read > 0);

	//fclose(src_file);
	 
	ret = zixi_get_stats(stream_handler, &conn_stats, &net_stats, &error_correction_stats);

	if(ret != ZIXI_ERROR_OK)
		printf("Failed to get statistics.\n");
	else
		printf("Stats:\n"
			   "===================================\n"
			   "Bitrate:\t%u\nTotal packets:\t%llu\nLost:\t%llu\n"
			   "===================================\n"
			  ,net_stats.bit_rate, net_stats.packets, error_correction_stats.not_recovered);

	ret = zixi_close_stream(stream_handler);
	if(ret != ZIXI_ERROR_OK)
	{
		printf("Failed to close stream\n");
		getchar();
		return 1;
	}

	if (cfg.local_nics)
		free(cfg.local_nics);

	printf("Done.\n");

#ifdef WIN32
	closesocket(s);
#else
	close(s);
#endif
		getchar();
	return 0;
}
