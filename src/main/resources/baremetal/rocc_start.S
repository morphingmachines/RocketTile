#include "riscv_test.h"

#define CUSTOM_X 0

#define RVTEST_XS_ENABLE                        \
  li a0, MSTATUS_XS & (MSTATUS_XS >> 1);        \
  csrs mstatus, a0;

    .macro init;
    RVTEST_XS_ENABLE
    .endm

start:

    RVTEST_CODE_BEGIN

    jal zero, _setup_sp_gp;

data:
.data 
RVTEST_DATA_BEGIN