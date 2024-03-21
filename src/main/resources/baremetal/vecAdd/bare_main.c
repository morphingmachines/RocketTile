
#include <stdint.h>
#include "exit_syscall.h"

uint32_t dataA[] = {0,1,2,3,4,5,6,7};
uint32_t dataB[] = {0,1,2,3,4,5,6,7};
uint32_t dataC[] = {0,2,4,6,8,10,12,14};

void bare_main(void)
{
  for(int i=0; i<7; i++){
    if(dataC[i] != (dataA[i] + dataB[i])){
      exit_fail(i);
    }
  }

  //-- If you have reached here, then everything seems good.
  exit_pass();
}
