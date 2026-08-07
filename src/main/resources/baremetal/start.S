#include "riscv_test.h"

#define CUSTOM_X 0

    RVTEST_RV32UF
start:

    RVTEST_CODE_BEGIN

    jal zero, _setup_sp_gp;

data:
.data 
RVTEST_DATA_BEGIN
