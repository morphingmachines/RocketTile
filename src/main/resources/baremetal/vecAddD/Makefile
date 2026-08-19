#/bin/bash

OUTNAME := $(notdir $(shell pwd))


-include ./../env.mk

TOP_LAZY_MODULE_CONFIG = ce.sim.SimDUT.RV32DConfig
RELATIVE_VERILATED_SIM_EXE = ./../$(SUFFIX_RELATIVE_VERILATED_SIM_EXE)

LINKFILE = ../linker.lds

# Use the D-extension ABI/march (double args/returns in FP regs) instead of
# the RV32 default (CFLAGS_RV32), so double arithmetic uses the FPU.
CFLAGS := $(filter-out $(CFLAGS_RV32),$(CFLAGS)) $(CFLAGS_RV32D)
LDFLAGS := $(filter-out $(CFLAGS_RV32),$(LDFLAGS)) $(CFLAGS_RV32D)

CPPFLAGS += -Wall -I./../
commonObjs = bare_main.o start.o startup.o

#LDFLAGS += -pg

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
