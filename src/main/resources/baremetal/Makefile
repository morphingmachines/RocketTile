#/bin/bash

OUTNAME := $(notdir $(shell pwd))

-include ./env.mk

#CPPFLAGS += -Wall -pg
CPPFLAGS += -Wall -I/home/madhav/rtl-workspace/rocket-rocc-examples/build/..
commonObjs = test.o rocc_start.o
#commonObjs = mmSeq.o

#LDFLAGS += -pg

LINKFILE = linker.lds
objects = $(commonObjs)
	
$(OUTNAME).elf : $(objects)
	$(LCC) $(LDFLAGS) -o $@ $^ $(LDLIBS) 

# "-ffp-contract=fast" compiler flag to use fmadd for fmul follwed by fadd
%.o : %.c
	$(CC) -ffp-contract=fast $(CPPFLAGS) $(CFLAGS) -c -o $@ $<

rocc_start.o: rocc_start.S
	$(CC) $(CFLAGS) -I/home/madhav/rtl-workspace/rocket-tools/riscv-tests/env/p -c -o $@ $<

test.o : test.c

.PHONY : clean
clean : 
	-rm -f *.elf *.o
