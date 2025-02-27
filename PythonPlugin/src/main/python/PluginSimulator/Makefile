CC=gcc

PYTHON_LIBS := $(shell pkg-config --cflags --libs python3)
LIBS=-lavutil -lavformat -lavcodec $(PYTHON_LIBS)  -I /usr/include/python3.*/ -L../ -lpythonWrapper

CFLAGS=-ggdb -flto
SRC=$(shell find . -name "*.c" )
OBJECTS=$(SRC:.c=.o)


all: pluginsimulator

pluginsimulator:$(OBJECTS)
	$(CC)  $(CFLAGS) -o  $@ $(addprefix ./build/,$(^F)) $(LIBS)

%.o:%.c
	$(CC) $(CFLAGS) -c -o $(addprefix ./build/,$(@F)) $^ $(LIBS)


export LD_LIBRARY_PATH=../
