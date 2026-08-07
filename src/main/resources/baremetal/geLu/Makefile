#/bin/bash

OUTNAME := $(notdir $(shell pwd))


-include ./../env.mk

TOP_LAZY_MODULE_CONFIG = ce.sim.SimDUT.RV32Config
RELATIVE_VERILATED_SIM_EXE = ./../$(SUFFIX_RELATIVE_VERILATED_SIM_EXE)

LINKFILE = ../linker.lds

# re_expf_asm takes/returns its argument in fa0 per the hard-float calling
# convention; override the default soft-float ABI to match at the call boundary.
CFLAGS := $(patsubst -mabi=ilp32,-mabi=ilp32f,$(CFLAGS))
LDFLAGS := $(patsubst -mabi=ilp32,-mabi=ilp32f,$(LDFLAGS))

CPPFLAGS += -Wall -I./../

# IMPL selects which re_expf_asm implementation to link: estrin (default) or horner.
IMPL ?= estrin
commonObjs = bare_main.o start.o startup.o re_expf_asm.o

#LDFLAGS += -pg

objects = $(commonObjs)

$(OUTNAME).elf : $(objects)
	$(CC) $(LDFLAGS) -o $@ $^ $(LDLIBS)

# "-ffp-contract=fast" compiler flag to use fmadd for fmul follwed by fadd
%.o : %.c
	$(CC) -ffp-contract=fast $(CPPFLAGS) $(CFLAGS) -c -o $@ $<

bare_main.o : bare_main.c

re_expf_asm.o : re_expf_asm_$(IMPL).S
	$(CC) $(CFLAGS) -c -o $@ $<

start.o: ../start.S
	$(CC) $(CFLAGS) -I$(RISCV_TESTS_SRC)/env/p -c -o $@ $<

startup.o : ../startup.c
	$(CC) -ffp-contract=fast $(CPPFLAGS) $(CFLAGS) -c -o $@ $<


.PHONY : clean
clean :
	-rm -f $(OUTNAME).elf $(OUTNAME).map *.o
