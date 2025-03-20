#include <assert.h>
#include <libavcodec/avcodec.h>
#include <libavcodec/codec.h>
#include <libavcodec/codec_par.h>
#include <libavformat/avformat.h>
#include <libavformat/avio.h>
#include <libavutil/avutil.h>
#include <libavutil/log.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>

extern void init_py_and_wrapperlib();
extern void releasejil();
extern void aquirejil();
extern void streamStarted(const char *streamid, int width, int height);
extern void streamFinished(const char *streamid);
extern void onVideoFrame(const char *streamid, AVFrame *avframe);
extern void init_python_plugin_state();
int execute;
void trap(int signal) { execute = 0; }

void my_log_callback(void *ptr, int level, const char *fmt, va_list vargs) {
  /*vprintf(fmt, vargs);*/
}

int main() {
  av_log_set_level(0);
  av_log_set_callback(my_log_callback);

  signal(SIGINT, &trap);
  execute = 1;

  aquirejil();
  init_py_and_wrapperlib();
  char *test = "stream1";
  releasejil();

  aquirejil();
  init_python_plugin_state();
  releasejil();

  AVFormatContext *avformatctx = avformat_alloc_context();

  assert(avformatctx);
  int ret = avformat_open_input(&avformatctx, "../test.mp4", NULL, NULL);
  if (ret < 0)
    printf("cannot open input file\n");

  avformat_find_stream_info(avformatctx, NULL);

  for (int i = 0; i < avformatctx->nb_streams; i++) {

    AVStream *stream = avformatctx->streams[i];
    const AVCodec *decoder =
        avcodec_find_decoder(avformatctx->streams[i]->codecpar->codec_id);
    AVCodecParameters *codecpar = avformatctx->streams[i]->codecpar;

    if (codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
      aquirejil();
      streamStarted(test, codecpar->width, codecpar->height);
      releasejil();

    } else {
      continue;
    }

    AVCodecContext *codec_ctx = avcodec_alloc_context3(decoder);
    avcodec_parameters_to_context(codec_ctx, codecpar);
    avcodec_open2(codec_ctx, decoder, NULL);

    AVPacket *pPacket = av_packet_alloc();
    AVFrame *pFrame = av_frame_alloc();

    const char *streamid = "stream1";

    int i = 10;
    while (av_read_frame(avformatctx, pPacket) >= 0) {
      int ret = avcodec_send_packet(codec_ctx, pPacket);
      if (ret < 0)
        continue;
      avcodec_receive_frame(codec_ctx, pFrame);
      aquirejil();
      onVideoFrame(streamid, pFrame);
      releasejil();

      if (!execute) {
        exit(0);
      }
      /*i--;*/
    }
    printf("video finished\n");
  }

  signal(SIGINT, SIG_DFL);
  return 0;
}
