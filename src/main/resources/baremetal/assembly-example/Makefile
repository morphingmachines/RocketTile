#/bin/bash

OUTNAME := $(notdir $(shell pwd))

-include ./../env.mk

CPPFLAGS += -Wall -I./../
commonObjs = riscv_rand_instr_test_0.o startup.o

#LDFLAGS += -pg

LINKFILE = link.ld
objects = $(commonObjs)
	
$(OUTNAME).elf : $(objects)
	$(CC) $(LDFLAGS) -o $@ $^ $(LDLIBS) 

# "-ffp-contract=fast" compiler flag to use fmadd for fmul follwed by fadd
%.o : %.c
	$(CC) -ffp-contract=fast $(CPPFLAGS) $(CFLAGS) -c -o $@ $<


riscv_rand_instr_test_0.o: riscv_rand_instr_test_0.S
	$(CC) $(CFLAGS) -I$(RISCV_TESTS_SRC)/env/p -c -o $@ $<

startup.o : ../startup.c
	$(CC) -ffp-contract=fast $(CPPFLAGS) $(CFLAGS) -c -o $@ $<


.PHONY : clean
clean : 
	-rm -f $(OUTNAME).elf $(OUTNAME).map *.o
