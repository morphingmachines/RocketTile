
#include <stdint.h>
#include "exit_syscall.h"

float dataA[] = {0,1,2,3,4,5,6,7};
float dataB[] = {0,1,2,3,4,5,6,7};
float dataC[] = {0,2,4,6,8,10,12,14};

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
