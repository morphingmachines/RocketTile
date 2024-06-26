#/bin/bash

#ifndef VERBOSE
#.SILENT:
#endif
XLEN ?= 32
RISCV_PREFIX ?= riscv$(XLEN)-unknown-elf
RISCV_GCC ?= $(RISCV_PREFIX)-gcc
TOP_LAZY_MODULE_CONFIG ?= ce.sim.SimDUT

CFLAGS_RV64=-mabi=lp64 -march=rv64imc
CFLAGS_RV32=-mabi=ilp32 -march=rv32imc

CC = $(RISCV_GCC)

CFLAGS = -ffunction-sections -fdata-sections -Wuninitialized -fno-builtin -ffreestanding  -static -mcmodel=medany
CFLAGS += -I${RISCV}/$(RISCV_PREFIX)/include


CFLAGS += -O3
#--print-gc-sections : prints removed sections
LDFLAGS = -nostdlib -nostartfiles -nodefaultlibs -Wl,-gc-sections -Wl,-print-gc-sections -Wl,-Map=$(OUTNAME).map -L$(RISCV)/$(RISCV_PREFIX)/lib -T$(LINKFILE) -O3

SUFFIX_RELATIVE_VERILATED_SIM_EXE = ../../../../generated_sv_dir/$(TOP_LAZY_MODULE_CONFIG)/obj_dir/VTestHarness


ifeq ($(XLEN), 64)
	CFLAGS += $(CFLAGS_RV64)
	LDFLAGS += $(CFLAGS_RV64)
else
	CFLAGS += $(CFLAGS_RV32)
	LDFLAGS += $(CFLAGS_RV32)
endif

check-build:
ifndef RISCV
	$(error RISCV must be set to rocket-tools (https://github.com/chipsalliance/rocket-tools) installation path.)
endif

ifndef RISCV_TESTS_SRC
	$(error RISCV_TESTS_SRC must be set to riscv-tests (rocket-tools submodule) source directory.)
endif

ifeq ($(XLEN), 32)
	make $(OUTNAME).elf
else
	make $(OUTNAME).elf XLEN=64
endif

run: $(OUTNAME).elf
	$(RELATIVE_VERILATED_SIM_EXE) $< 2>&1 | spike-dasm
