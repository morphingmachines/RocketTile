# Replace 'gcd' with your %PROJECT-NAME%
project = ce

TARGET ?= RV32

# Toolchains and tools
MILL = ./../playground/mill

-include ../playground/Makefile.include

# Targets
rtl: check-firtool ## Generates Verilog code from Chisel sources (output to ./generated_sv_dir)
	$(MILL) $(project).runMain $(project).ceMain $(TARGET)

check: verilate
.PHONY: test
test: check-firtool ## Run Chisel tests
	$(MILL) $(project).test.testOnly $(project).CeTileTests
	@echo "If using WriteVcdAnnotation in your tests, the VCD files are generated in ./test_run_dir/testname directories."

.PHONY: verilate 
verilate: check-verilator check-firtool check-env ## Generate Verilator simulation executable for  TARGET = RV32 (default) or RV64 or RV32RoCC or RV64RoCC 
	$(MILL) $(project).runMain $(project).TestLazyMain $(TARGET)

.PHONY: check-env
check-env:
ifndef RISCV
	$(error RISCV environment variable is not defined)
endif	