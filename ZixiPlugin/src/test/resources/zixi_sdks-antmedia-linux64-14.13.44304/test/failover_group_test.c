#include <stdio.h>
#include <stdlib.h>
#ifdef _WIN32
#define WIN32_MEAN_AND_LEAN
#include <WinSock2.h>
#include <windows.h>
#else
#include <stdint.h>
#include <time.h>

void Sleep(int ms)
{
	struct timespec required, remaining;
	required.tv_sec = ms / 1000;
	required.tv_nsec = 1000000 * (ms % 1000);	//	nanoseconds = 10^6 milliseconds
	nanosleep(&required, &remaining);
}

#endif

#include "../interface/zixi_client_interface.h"


void logger( void* user_data, int level, const char* str )
{
	//OutputDebugStringA(str);
	printf( "%s", str );
}


enum PARAMETERS_ENUM
{
	EXEC_PATH_PARAM = 0,
	LATENCY_PARAM,
	LOG_LEVEL,
	OUT_HOST,
	OUT_PORT,
	MODE,
	SOURCE_URL_PARAMS
};


int init_client_handle( char *sz_url, void *handle );
void print_stats(char **sz_urls, void *hadle, void **components, int count);

int main( int argc, char *argv[] )
{
	if (argc < SOURCE_URL_PARAMS + 1)
	{
		printf("Use: tester <search_window_ms> <log_level> <output_host> <output_port> <mode> <url> <url> [<url> ...]\n");
		return 1;
	}

	int latency = atoi(argv[LATENCY_PARAM]);
	int log_level = atoi(argv[LOG_LEVEL]);
	char *output_host = argv[OUT_HOST];
	int output_port = atoi(argv[OUT_PORT]);
	int mode = atoi(argv[MODE]);
	int ret = 0;
#ifdef USE_SELECT_FOR_NOTIFICATION
	int fd, tid;
	int native_socket;
	fd_set s;

	struct timeval to = { 0 };
	to.tv_sec = 0;
#endif
	if ( (output_port <= 0) || (output_port >= 0x0FFFF) )
	{
		printf("Output port value error: %d\n", output_port);
		return 1;
	}
	
	if ( (log_level < -1) || (log_level > 5) )
	{
		printf("Log level expected to be between -1[off] to 5[Fatal errors only]\n");
		return 1;
	}
	
	if (mode <0 || mode > 2)
	{ 
		printf("mode must be between 0 and 2\n");
		return 1;
	}

	size_t clients_count = argc - (size_t)(SOURCE_URL_PARAMS);
	if (clients_count < 2)
	{
		printf( "Detected only %llu clients\n", clients_count );
		return 1;
	}

	ret = zixi_init();
	if (ret != ZIXI_ERROR_OK)
	{
		printf( "zixi_init ERROR - %d\n", (int)ret );
		zixi_destroy();
		return 1;
	}

	zixi_client_configure_logging(log_level, logger, 0);
	
	void *group_handle = 0;
	void **clients_handler = 0;
	int *priorities = malloc(sizeof(int) * clients_count);
	if (priorities == 0)
	{
		printf("zixi_init ERROR - %d\n", (int)ret);
		zixi_destroy();
		return 1;
	}
	int i = 0;
	for (; i < clients_count; i++)
	{
		priorities[i] = i % 2;
	}

	ret =  zixi_init_failover_group(&group_handle, clients_count, &clients_handler, latency, priorities, 20000000, mode);
	if (ret != ZIXI_ERROR_OK)
	{
		printf("Failed to init failover group, error: %d\n", ret);
		zixi_destroy();
		return 1;
	}

	int parameter_count = SOURCE_URL_PARAMS;
	for (; parameter_count < argc; ++parameter_count)
	{
		ret = init_client_handle(argv[parameter_count], clients_handler[parameter_count - SOURCE_URL_PARAMS]);
		if (ret != ZIXI_ERROR_OK)
		{
			printf("Failed to init %s, error: %d\n", (char*)argv[parameter_count], ret);
			zixi_delete_failover_group(group_handle);
			zixi_destroy();
			return 1;
		}
	}

	//	Make sure error is returned here.
	ret = zixi_configure_ts_output_stream ( group_handle, output_host, output_port );
	if (ret != ZIXI_ERROR_OK)
	{
		printf("Failed to setup output. Error: %d\n", ret);
		zixi_delete_failover_group( group_handle );
		zixi_destroy();
		return 1;
	}
#ifdef USE_SELECT_FOR_NOTIFICATION
	ret = zixi_get_descriptors(group_handle, &fd, &tid, &native_socket);
	if (ret != ZIXI_ERROR_OK)
	{
		printf("zixi_get_descriptors ERROR - %d\n", ret);
		zixi_delete_failover_group(group_handle);
		zixi_destroy();
		return 1;
	}
	
#endif

	int cycles = 0;
	while(1)
	{
		char buffer[188*7];
		unsigned int result_size = 0;
		bool eof, disc;
		int bitrate = 0;
#ifdef USE_SELECT_FOR_NOTIFICATION
		FD_ZERO(&s);
		FD_SET(fd, &s);
		if (select(fd + 1, &s, 0, 0, &to) > 0)
		{
			ret = zixi_read(group_handle, buffer, 188 * 7, &result_size, &eof, &disc, true, &bitrate);
		}
		else
#else		
		ret = zixi_read(group_handle, buffer, 188*7, &result_size, &eof, &disc, true, &bitrate);
		if (ret == ZIXI_ERROR_NOT_READY || ret == ZIXI_ERROR_NOT_CONNECTED)
#endif //USE_SELECT_FOR_NOTIFICATION
		{
#ifdef _WIN32
			Sleep(10);
#else
			usleep(2000);
#endif // _WIN32
			continue;
		}
		if ((ret != ZIXI_ERROR_OK) && (ret != ZIXI_ERROR_NOT_READY))
		{
			printf("zixi_read ERROR - %d\n", ret);
			zixi_disconnect(group_handle);
			zixi_delete_failover_group(group_handle);
			zixi_destroy();

			return (ret == ZIXI_ERROR_EOF) ? 0 : 1;
		}

		++cycles;
		if (cycles % 1000 == 0)
		{
			print_stats(argv + SOURCE_URL_PARAMS, group_handle, clients_handler, clients_count);
		}
	}

	zixi_delete_failover_group( group_handle );
	zixi_destroy();

	return 0;
}


int init_client_handle( char *sz_url, void *handle )
{
	ZIXI_CALLBACKS cbs;

	int ret = zixi_configure_id( handle, "me", "" );
	if (ret != ZIXI_ERROR_OK)
		return ret;

	cbs.user_data = NULL;
	cbs.zixi_bitrate_changed = NULL;
	cbs.zixi_new_stream = NULL;
	cbs.zixi_status_changed = NULL;

	ret = zixi_configure_error_correction(handle, 1000, ZIXI_LATENCY_STATIC, ZIXI_FEC_ADAPTIVE, 30, 30, false, false, 0, false);
	if (ret != ZIXI_ERROR_OK)
		return ret;

	ret = zixi_connect_url( handle, sz_url, false, cbs, true, false, false);

	return ret;	
}


void print_stats( char **sz_urls, void *group_handle, void **components, int count )
{
	int i, ret;
	ZIXI_NETWORK_STATS net_stats = {0};
	ZIXI_ERROR_CORRECTION_STATS error_correction_stats = {0};
	ZIXI_CONNECTION_STATS con_stats = {0};

	ret = zixi_query_statistics( group_handle, &con_stats, &net_stats, &error_correction_stats );
	if (ret != ZIXI_ERROR_OK)
	{
		printf( "Failed to query group stats, error: %d\n", ret );
		return;
	}

	ZIXI_FAILOVER_COMPONENT_STATS *zfs = (ZIXI_FAILOVER_COMPONENT_STATS *) malloc( sizeof( ZIXI_FAILOVER_COMPONENT_STATS ) * count );
	ret = zixi_query_group_statistics( group_handle, zfs, count );
	if (ret != ZIXI_ERROR_OK)
	{
		printf( "Failed to query group stats, error: %d\n", ret );
		free( zfs );
		return;
	}
	printf("\n##main\t"
		"Bitrate=%u\t"
		"packets=%llu\t"
		"dropped=%llu\n",
		net_stats.bit_rate,
		net_stats.packets,
		net_stats.dropped);
		

	for (i = 0; i < count; ++i)
	{
		printf("%s\t\t", sz_urls[i]);
		ZIXI_NETWORK_STATS net_stats ={ 0 };
		ZIXI_ERROR_CORRECTION_STATS error_correction_stats ={ 0 };
		ZIXI_CONNECTION_STATS con_stats ={ 0 };

		ret = zixi_query_statistics(components[i], &con_stats, &net_stats, &error_correction_stats );
		if (ret != ZIXI_ERROR_OK)
		{
			printf("Failed to query component stats.\n");
			continue;
		}

		printf(
			"Bitrate=%u\t"
			"packets=%llu\t"
			"dropped=%llu\t"
			"recovered=%llu\t"
			"lost=%llu\t"
			"nulls=%llu\t"
			"latency=%dms\t"
			"contributed=%llu\t"
			"stream latency=%d\n",
			net_stats.bit_rate,
			net_stats.packets,
			net_stats.dropped,
			(error_correction_stats.arq_recovered + error_correction_stats.fec_recovered),
			error_correction_stats.not_recovered,
			error_correction_stats.nulls_stuffed,
			(int)net_stats.latency,
			zfs[i].contributed_packets, 
			zfs[i].last_packet_latency_ms);
	}

	free(zfs);
}

