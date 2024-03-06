#include "riscv_test.h"

//extern void bare_main(void) __attribute__((noreturn));

#define CUSTOM_X 0

#define RVTEST_XS_ENABLE                        \
  li a0, MSTATUS_XS & (MSTATUS_XS >> 1);        \
  csrs mstatus, a0;

    .macro init;
    RVTEST_XS_ENABLE
    .endm

start:

    RVTEST_CODE_BEGIN

    jal zero, _enter;

data:
.data 
RVTEST_DATA_BEGIN