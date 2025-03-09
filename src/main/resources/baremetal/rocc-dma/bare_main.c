
#include <stdint.h>
#include "exit_syscall.h"
#include "include/dma.h"

void assert(int cond, int v)
{
  if (cond <= 0)
  {
    exit_fail(v);
  }
}

static inline __attribute__((always_inline)) void start_dma()
{
  doWrite(CONTROL_REG, 1)
}

void bare_main(void)
{

  doWrite(SRCADDR_REG, 0x80010000);
  doWrite(DSTADDR_REG, 0x80020000);
  doWrite(BYTELEN_REG, 128);

  start_dma();

  uint32_t done = 0;
  doRead(done, STATUS_REG);
  while ((done & 0xE) == 0) // Is DMA done or DMA error
  {
    assert((done & 0x1) == 1, 1); // DMA is busy
    doRead(done, STATUS_REG);
  }

  assert((done & 0xC) == 0, 2); // DMA done without errors

  //-- If you have reached here, then everything seems good.
  exit_pass();
}