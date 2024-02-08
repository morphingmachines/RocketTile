// The system contains only one RISC-V Hart.
// The CLINT base address 0x2000000 holds the MSIP bit for the Hart.
// mtvec is read-only and hard-coded to 0x10000 (ROM_BASE) same as "_start" below.
// mtvec is configured to direct-mode.


#define DRAM_BASE  0x80000000
#define CLINT_BASE 0x02000000

// boot all cores (only hart 0) and jump to main program execution
.section .text.start, "ax", @progbits
.globl _start
_start:
  j boot_core_hart0

// this boot ROM doesn't know about any boot devices, so it just spins,
// waiting for the serial interface to load the program and interrupt it
.section .text.hang, "ax", @progbits
.globl _hang
_hang: // reset vector
  li a0, 8           // MIE or MSIE bit
  csrw mie, a0       // set only MSIE in MIE CSR
  csrs mstatus, a0   // set MIE in mstatus CSR
wfi_loop: // wait for MSIP interrupt to start program execution
  wfi
  j wfi_loop

boot_core_hart0:   
  li a1, CLINT_BASE	// base address of clint
  sw zero, 0(a1)   	// clear the interrupt MSIP
  li a0, DRAM_BASE 	// base address of DRAM
  csrw mepc, a0    	// return from interrupt to start of user program
  li a2, 0x80      	// set mstatus MPIE to 0, to disable interrupts
  csrc mstatus, a2
  mret

_dtb:
