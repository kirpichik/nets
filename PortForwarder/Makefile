# Compiler
CC=cc

# Compiler flags
INCLUDES=
CFLAGS=-Wall -std=gnu99 -O2 $(INCLUDES)
LDFLAGS=

# Sources
SOURCES=main.c

# Compiler output
EXECUTABLE=forwarder

all: $(EXECUTABLE)

$(EXECUTABLE): $(SOURCES)
	$(CC) $(CFLAGS) $(SOURCES) -o $@ $(LDFLAGS)

clean:
	rm -rf $(EXECUTABLE)

clear: clean

rebuild: clean all

