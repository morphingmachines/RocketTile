
#include <stdint.h>
#include "exit_syscall.h"
#include "include/accumulator.h"

void assert(int cond, int v)
{
  if (cond <= 0)
  {
    exit_fail(v);
  }
}

void bare_main(void)
{

  volatile uint64_t data[] = {0xdead, 0xbeef, 0x0bad, 0xf00d}, y;

  uint16_t addr = 1;
  doWrite(y, addr, data[0]);

  doRead(y, addr);
  assert(y == data[0], 1);

  uint64_t data_accum = -data[0] + data[1];
  doAccum(y, addr, data_accum);
  assert(y == data[0], 2);

  doRead(y, addr);
  assert(y == data[1], 3);

  doLoad(y, addr, &data[2]);
  assert(y == data[1], 4);

  doRead(y, addr);
  assert(y == data[2], 5);

  //-- If you have reached here, then everything seems good.
  exit_pass();
}