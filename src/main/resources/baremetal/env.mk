#/bin/bash

#ifndef VERBOSE
#.SILENT:
#endif

#TODO: use absolute paths of the clang and riscv-gcc
#CC = $(REDEFINE_LLVMPATH)/clang
CC = riscv64-unknown-elf-gcc

#TODO:-fno-builtin-memset may make code unsafe
CFLAGS = -ffunction-sections -fdata-sections -Wuninitialized -fno-builtin -ffreestanding  -static -mcmodel=medany
#CFLAGS += -fno-addrsig
#CFLAGS += -march=rv32imf
CFLAGS += -O3
LCC = riscv64-unknown-elf-ld
#LCC = $(REDEFINE_LLVMPATH)/ld.lld
#--print-gc-sections : prints removed sections
#LDFLAGS = -nostdlib -nostartfiles -nodefaultlibs -Wl,-gc-sections -Wl,-print-gc-sections -L$(REDEFINE_LIB)/ldscript -T$(LINKFILE) -O3
LDFLAGS = -gc-sections -print-gc-sections  -T$(LINKFILE) -O3
