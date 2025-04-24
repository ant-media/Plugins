#include "../build/libpythonWrapper.h"
#include <Python.h>
#include <assert.h>
#include <libavformat/avformat.h>
#include <libavutil/frame.h>
#include <stdint.h>
#include <stdio.h>

PyGILState_STATE gstate;

void aquirejil() {
  if (!PyGILState_Check())
    gstate = PyGILState_Ensure();
  else
    printf("jil is already aquired");
}
void releasejil() { PyGILState_Release(gstate); }

void init_py_and_wrapperlib() {
  printf("initializing python\n");
  int ret = PyImport_AppendInittab("libpythonWrapper", PyInit_libpythonWrapper);
  assert(ret > 0);
  Py_Initialize();
  printf("importing libpythonWrapper\n");
  PyObject *module = PyImport_ImportModule("libpythonWrapper");
  assert(module != NULL);
  printf("python initialization finished\n");
}

void yuv_to_rgb(AVFrame *avframe, uint8_t inplace) {}
