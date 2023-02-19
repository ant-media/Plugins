#include <stdio.h>
#include <stdlib.h>
#ifdef _WIN32
#define WIN32_MEAN_AND_LEAN
#include <WinSock2.h>
#include <windows.h>
#else
#include <stdint.h>
#include <unistd.h>
#include <sys/time.h>
#include <time.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#endif

#include "../interface/zixi_client_interface.h"

#ifdef WIN32
#define ftello64 _ftelli64
#define fseeko64 _fseeki64
#endif

#ifndef UINT32_MAX
#define UINT32_MAX ((unsigned int)0xffffffff)
#endif

//#define ZIXI_DOWNLOAD_CLIENT

//#define USE_SELECT_FOR_NOTIFICATION

enum
{
	PARAM_URL = 1,
	PARAM_LATENCY,
	PARAM_LOGLEVEL,
	PARAM_HOST_OUT,
	PARAM_PORT_OUT,

	PARAM_COUNT
};

void logger(void* user_data, int level, const char* str)
{
	//OutputDebugStringA(str);
	printf("%s", str);
}

unsigned int getTickCount()
{
#ifndef WIN32
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return (unsigned int)(tv.tv_sec * 1000 + tv.tv_usec / 1000);
#else
	return GetTickCount();
#endif
}


static void status_changed_handler(void *handle, ZIXI_STATUS status, void *user_data)
{
	int err = zixi_get_last_error(handle);

         switch (status)
         {
         case ZIXI_DISCONNECTED:
                  printf("Disconnected\n");
                  break;
         case ZIXI_CONNECTING:
              //    fprintf(stderr,"\t ZIXI_STATUS -> ZIXI_CONNECTING\n");
                  break;
         case ZIXI_CONNECTED:
                //  fprintf(stderr,"\t ZIXI_STATUS -> ZIXI_CONNECTED\n");
                          break;
         case ZIXI_DISCONNECTING:
                 // fprintf(stderr,"\t ZIXI_STATUS -> ZIXI_DISCONNECTING\n");
                  break;
         case ZIXI_RECONNECTING:
                 // fprintf(stderr,"\t ZIXI_STATUS -> ZIXI_RECONNECTING\n");
                  break;
         default:
                  fprintf(stderr,"\t ZIXI_STATUS -> UNKNOWN[status=%d]\n",status);
                  break;
         }

		 if (err != 0)
		 fprintf(stderr, "err=%d\n", err);

}

void stream_info_handler(void *handle, ZIXI_STREAM_INFO info, void *user_data)
{
}

int main(int argc, char* argv[])
{
	int major = 0;
	int minor = 0;
	int minor_minor = 0;
	int build = 0;
	ZIXI_CALLBACKS cbs;
	int ret = 0;
#ifdef USE_SELECT_FOR_NOTIFICATION
	int fd, tid;
	int native_socket;
#endif

	int diff_ms;
	int br;
	//char input = 0;
	unsigned char frameBuffer[1316*8] = {0};
	ZIXI_NETWORK_STATS net_stats = {0};
	ZIXI_ERROR_CORRECTION_STATS error_correction_stats = {0};
	ZIXI_CONNECTION_STATS con_stats = {0};
	void *zixi_handle = NULL;
	unsigned long long total = 0, prev_total = 0;

#ifdef ZIXI_DOWNLOAD_CLIENT
	unsigned long long fsize;
#endif

	bool is_eof = false;
	bool disc = false;
	unsigned int now, start, prev_time, bytes_read = 0;
	ZIXI_STREAM_INFO info;
	unsigned short port_out = 0;
	bool fec;
	unsigned int fec_overhead;
	unsigned int fec_block_ms;
	bool fec_content_aware;
	int latency = 300;
	//char *local_nic;
	
#ifndef WIN32
	//fd_set s;

	struct timeval to = {0};
	to.tv_sec = 0;
#endif
	int log_level = -1;
//	FILE* out_file = 0;

	if (argc != PARAM_COUNT && argc != PARAM_COUNT-1 && argc != PARAM_COUNT-2 && argc != PARAM_COUNT-3)
	{
		printf("Usage: client_interface_tester <url> <latency> <log-level> <host_out> <port_out>\n");
		return 1;
	}

        if (argc > PARAM_LATENCY)
                latency = atoi(argv[PARAM_LATENCY]);


	if (argc > PARAM_LOGLEVEL)
		log_level = atoi(argv[PARAM_LOGLEVEL]);
	else 
		log_level = -1;

	zixi_client_configure_logging(log_level, logger, 0);

	//out_file = fopen(argv[PARAM_OUTFILE], "ab");

//	if (out_file)
//	{
//		fseeko64(out_file, 0, SEEK_END);
//		fsize = ftello64(out_file);
//	}
//	else
//	{
//		fsize = 0;
//		out_file = fopen(argv[PARAM_OUTFILE], "wb");
//	}

//	if (!out_file)
//		printf("failed opening %s for writing\n", argv[PARAM_OUTFILE]);

	// init callbacks struct
	cbs.zixi_new_stream = stream_info_handler;
	cbs.zixi_status_changed = status_changed_handler;
	cbs.zixi_bitrate_changed = NULL;

	// run zixi_init(...) to load libzixiClient dll/so
	ret = zixi_init();
	if (ret != 0)
	{
		printf("zixi_init ERROR - %d\n", ret);
		zixi_destroy();
		return 1;
	}

	ret = zixi_init_connection_handle(&zixi_handle);
	if (ret != 0)
	{
		printf("zixi_init_connection_handle ERROR - %d\n", ret);
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		return 1;
	}
	
	if (argc > PARAM_PORT_OUT)
	{
		port_out = atoi(argv[PARAM_PORT_OUT]);
		printf("zixi_configure_ts_output_stream , host: %s , port: %d\n", argv[PARAM_HOST_OUT] , port_out);
		zixi_configure_ts_output_stream (zixi_handle ,argv[PARAM_HOST_OUT] ,port_out); 

	}



	ret = zixi_configure_id(zixi_handle, "test_client", "");
	if (ret != 0)
	{
		printf("zixi_configure_id ERROR - %d\n", ret);
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		return 1;
	}

	// Configure progressive download
	
#ifdef ZIXI_DOWNLOAD_CLIENT
/*	ret = zixi_configure_pdl(zixi_handle, 128000000, NULL, NULL);
	if (ret != 0)
	{
		printf("zixi_configure_pdl ERROR - %d\n", ret);
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		return 1;
	}
	*/
#endif

	ret = zixi_client_version(&major, &minor, &minor_minor, &build);
	if (ret != ZIXI_ERROR_OK)
	{
		printf("zixi_client_version ERROR - %d\n", ret);
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		return 1;
	}

	printf("version:%d.%d.%d.%d\n", major, minor, minor_minor, build);
	
	
	/*init fec*/
	fec               = false;
	fec_overhead      = 30;
	fec_block_ms      = 50;
	fec_content_aware = false;

	zixi_configure_error_correction(zixi_handle, latency, ZIXI_LATENCY_STATIC, fec?ZIXI_FEC_ON:ZIXI_FEC_OFF, fec_overhead, fec_block_ms, fec_content_aware, false, 0, false, ZIXI_ARQ_ON);

	//local_nic= "10.7.0.154"; 
	//zixi_configure_local_nic(zixi_handle, local_nic, 0);

#ifdef ZIXI_DOWNLOAD_CLIENT
	if (fsize > 0)
	{
		printf("File exists. Append[y/n]? ");
		input = getchar();
		if (input != 'y' && input != 'Y') 
		{
			fsize = 0;
			//fclose(out_file);
			//out_file = fopen(argv[PARAM_OUTFILE], "wb");
			//if (!out_file)
			{
				printf("Failed to open %s for writing", argv[PARAM_OUTFILE]);
				zixi_delete_connection_handle(zixi_handle);
				zixi_destroy();
				return -1;
			}
		}
	}
	ret = zixi_download_url(zixi_handle, argv[PARAM_URL], 0, fsize, cbs, false);
#else
	ret = zixi_connect_url(zixi_handle, argv[PARAM_URL], true, cbs, true, false, true, "");
#endif
	if (ret != ZIXI_ERROR_OK)
	{
		int ex_ret = zixi_get_last_error(zixi_handle);
		printf("zixi_connect_url ERROR - %d, last error - %d\n", ret, ex_ret);
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		return 1;
	}


	ret = zixi_query_stream_info(zixi_handle, &info);
	if (ret != ZIXI_ERROR_OK)
	{
		printf("zixi_query_stream_info ERROR - %d\n", ret);
		zixi_disconnect(zixi_handle);
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		return 1;
	}

	printf("connected. size=%lldMB\n", (info.file_size+500000)/1000000);
/*	if (fsize >= info.m_file_size)
	{
		if (fsize == info.m_file_size)
			printf("File already complete\n");
		else
			printf("Remote file smaller than local file - aborting.\n");

		//fclose(out_file);
		zixi_disconnect(zixi_handle);
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		return 0;
	}
*/

#ifdef USE_SELECT_FOR_NOTIFICATION
	ret = zixi_get_descriptors(zixi_handle, &fd, &tid, &native_socket);
	if (ret != ZIXI_ERROR_OK)
	{
		printf("zixi_get_descriptors ERROR - %d\n", ret);
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		return 1;
	}
#endif

	prev_time = start = getTickCount();
//	printf("fd=%d tid=%d\n", fd, tid);

	// get 10MB
#ifdef ZIXI_DOWNLOAD_CLIENT
	is_eof = false;
	while (!is_eof)
#else
	while (1)
#endif
	{

		if (argc < PARAM_COUNT)
		{
			is_eof = false;
#ifdef USE_SELECT_FOR_NOTIFICATION
			FD_ZERO(&s);
			FD_SET(fd,&s);
			if (select(fd+1, &s, 0, 0, &to) > 0)
			{
				ret = zixi_read(zixi_handle, (char*)frameBuffer, 1316*8, &bytes_read, &is_eof, &disc, false, &br);
			}
			else
#else			
				ret = zixi_read(zixi_handle, (char*)frameBuffer, 1316*8, &bytes_read, &is_eof, &disc, false, &br);

//			if (bytes_read)
//				printf("read returned size=%d\n", bytes_read);
			if (ret == ZIXI_ERROR_NOT_READY && !is_eof)
#endif
			{
#ifdef _WIN32
				Sleep(10);
#else
				usleep(2000);
#endif
				continue;
			}

			if ((ret != ZIXI_ERROR_OK)&&(ret != ZIXI_ERROR_NOT_READY))
			{
				printf("zixi_read ERROR - %d\n", ret);
				zixi_disconnect(zixi_handle);
				zixi_delete_connection_handle(zixi_handle);
				zixi_destroy();

				return (ret == ZIXI_ERROR_EOF)?0:1;
			}
		}
		else
		{
#ifdef _WIN32
			Sleep(100);
#else
			usleep(100000);
#endif
		}

		now = getTickCount();
		diff_ms = (int)(now - prev_time); 
		if (diff_ms < 0)
			diff_ms += UINT32_MAX;

		if (diff_ms > 1000)
		{
			prev_time = now;
			prev_total = total;

			ret = zixi_query_statistics(zixi_handle, &con_stats, &net_stats, &error_correction_stats);

			if (ret == ZIXI_ERROR_OK)
			{
				printf(
					"Total %lluMB\t"
					"Speed=%lldkbps\t"
					"bitrate=%u\t"
					"packets=%llu\t"
					"dropped=%llu\t"
					"recovered=%llu\t"
					"lost=%llu\t"
					"nulls=%llu\t" 
					"latency=%ums\n",
					(total/1000000), 
					((total - prev_total)*8 / diff_ms),
					net_stats.bit_rate,
					net_stats.packets, 
					net_stats.dropped, 
					(error_correction_stats.arq_recovered + error_correction_stats.fec_recovered), 
					error_correction_stats.not_recovered, 
					error_correction_stats.nulls_stuffed,
					net_stats.latency);
			}
		}

		total += bytes_read;
		//fwrite(frameBuffer, 1, bytes_read, out_file);
	}
	//fclose(out_file);

	now = getTickCount();
	diff_ms = now - start;
	printf("Done. Average speed=%dkbps\n", (int)((total*8) / diff_ms));

	ret = zixi_query_statistics(zixi_handle, &con_stats, &net_stats, &error_correction_stats);

	if (ret == ZIXI_ERROR_OK)
	{
		printf("\nStatistics:\nbitrate %u\n", net_stats.bit_rate);
		printf("packets %llu\n", net_stats.packets);
		printf("dropped %llu\n", net_stats.dropped);
		printf("recovered %llu\n", error_correction_stats.arq_recovered + error_correction_stats.fec_recovered);
		printf("lost %llu\n", error_correction_stats.not_recovered);
		printf("dup %llu\n", error_correction_stats.duplicates);
	}

	// disconnect from the server
	ret = zixi_disconnect(zixi_handle);

	if (ret != ZIXI_ERROR_OK)
	{
		zixi_delete_connection_handle(zixi_handle);
		zixi_destroy();
		printf("zixi_disconnect ERROR - %d\n", ret);

		return 1;
	}

	ret = zixi_delete_connection_handle(zixi_handle);
	if (ret != ZIXI_ERROR_OK)
	{
		zixi_destroy();
		printf("zixi_delete_connection_handle ERROR - %d\n", ret);

		return 1;
	}


	// unload libzixiClient dll/so
	ret = zixi_destroy();

	printf("Done.\n");

	return 0;
}

