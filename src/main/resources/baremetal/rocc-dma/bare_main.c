
#include <stdint.h>
#include "exit_syscall.h"
#include "include/dma.h"
#include "cache_op.h"

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

#define NUMWORDS 32

int src[NUMWORDS] __attribute__((aligned(64)));
int dst[NUMWORDS] __attribute__((aligned(64)));

void bare_main(void)
{

  for(int i=0; i<NUMWORDS; i++){
    src[i] = i;
    dst[i] = 0;
  }


  doWrite(SRCADDR_REG, src);
  doWrite(DSTADDR_REG, dst);
  doWrite(BYTELEN_REG, NUMWORDS * sizeof(int));

  start_dma();

  uint32_t done = 0;
  doRead(done, STATUS_REG);
  while ((done & 0xE) == 0) // Is DMA done or DMA error
  {
    assert((done & 0x1) == 1, 1); // DMA is busy
    doRead(done, STATUS_REG);
  }

  assert((done & 0xC) == 0, 2); // DMA done without errors

  for(int i=0; i<NUMWORDS; i++){
    assert(src[i] == dst[i], 3);
  }  

  //-- If you have reached here, then everything seems good.
  exit_pass();
}