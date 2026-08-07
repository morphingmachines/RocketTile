
#include <stdint.h>
#include "exit_syscall.h"

extern float re_expf_asm(float x);

#define TOLERANCE  1e-5f
#define NUM_INPUTS 8
#define REPEAT     128

float dataX[]        = {-10.0f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 2.0f, 5.0f};
float dataExpected[] = {4.539993096841499e-05f, 0.3678794503211975f, 0.6065306663513184f,
                         1.0f, 1.6487212181091309f, 2.7182817459106445f, 7.389056205749512f,
                         148.4131622314453f};

#define MTIME_LO ((volatile uint32_t *)0x0200bff8UL)

/* Assumes mtime does not overflow 32 bits over the measured window. */
static inline uint32_t read_mtime(void)
{
  return *MTIME_LO;
}

void bare_main(void)
{
  for(int i=0; i<NUM_INPUTS; i++){
    float result = re_expf_asm(dataX[i]);
    if(__builtin_fabsf(result - dataExpected[i]) > TOLERANCE){
      exit_fail(i);
    }
  }

  uint32_t start = read_mtime();
  for(int r=0; r<REPEAT; r++){
    for(int i=0; i<NUM_INPUTS; i++){
      re_expf_asm(dataX[i]);
    }
  }
  uint32_t elapsed = read_mtime() - start;
  int avg_cycles = (int)(elapsed / (REPEAT * NUM_INPUTS));

  /* Report avg_cycles through the tohost fail channel: exit_fail(num) sets
   * gp = num*2+1, printed by the sim/fesvr as tohost on exit.
   * Recover with: avg_cycles = (tohost - 1) / 2
   */
  exit_fail(avg_cycles);
}
