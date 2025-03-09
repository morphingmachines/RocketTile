// See LICENSE for license details.

#ifndef SRC_MAIN_C_RoCC_DMA_H
#define SRC_MAIN_C_RoCC_DMA_H

#include "rocc-software/src/xcustom.h"

/**   - 0 : Control Register
 *   - 1 : Status Register
 *   - 2 : Interrupt Clear Register
 *   - 3 : Source Address Register
 *   - 4 : Destination Address Register
 *   - 5 : Length Register
 */

#define CONTROL_REG 0
#define STATUS_REG 1
#define CLEAR_REG 2
#define SRCADDR_REG 3
#define DSTADDR_REG 4
#define BYTELEN_REG 5

#define XCUSTOM_ACC 0

#define doWrite(regAddr, data) \
  ROCC_INSTRUCTION_0_R_R(XCUSTOM_ACC, data, regAddr, 0);
#define doRead(y, regAddr) \
  ROCC_INSTRUCTION_R_R_R(XCUSTOM_ACC, y, 0, regAddr, 0);

#endif // SRC_MAIN_C_RoCC_DMA_H
