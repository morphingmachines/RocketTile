#/bin/bash

OUTNAME := $(notdir $(shell pwd))

-include ./../env.mk

CPPFLAGS += -Wall -I./../
commonObjs = bare_main.o start.o startup.o

#LDFLAGS += -pg

LINKFILE = ../linker.lds
objects = $(commonObjs)
	
$(OUTNAME).elf : $(objects)
	$(CC) $(LDFLAGS) -o $@ $^ $(LDLIBS) 

# "-ffp-contract=fast" compiler flag to use fmadd for fmul follwed by fadd
%.o : %.c
	$(CC) -ffp-contract=fast $(CPPFLAGS) $(CFLAGS) -c -o $@ $<

bare_main.o : bare_main.c

start.o: ../start.S
	$(CC) $(CFLAGS) -I$(RISCV_TESTS_SRC)/env/p -c -o $@ $<

startup.o : ../startup.c
	$(CC) -ffp-contract=fast $(CPPFLAGS) $(CFLAGS) -c -o $@ $<


.PHONY : clean
clean : 
	-rm -f $(OUTNAME).elf $(OUTNAME).map *.o
