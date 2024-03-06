//#include "riscv_test.h"

void _enter(void) {
    // Setup SP and GP
    // The locations are defined in the linker script
    __asm__ volatile  (
        ".option push;"
        // The 'norelax' option is critical here.
        // Without 'norelax' the global pointer will
        // be loaded relative to the global pointer!
         ".option norelax;"
        "la    gp, __global_pointer$;"
        ".option pop;"
        "la    sp, _sp;"
        "jal   zero, bare_main;"
        :  /* output: none %0 */
        : /* input: none */
        : /* clobbers: none */); 
    // This point will not be executed, _start() will be called with no return.
}


static inline __attribute__((always_inline)) void exit_pass(void) {
    __asm__ volatile(
        "fence;"
        "li gp, 1;"
        "li a7, 93;"
        "li a0, 0;"
        "ecall;"
        : /* output: none %0 */
        : /* input: none */
        : /* clobbers: none*/
        // This point will not be executed, system exit function will be called with no return
    );
}

static inline __attribute__((always_inline)) void exit_fail(int num) {
    if(num == 0){
        num = -1;
    }

    int testnum = num*2+1;
    
    __asm__ volatile(
        "fence;"
        "addi gp, %[testnum], 0;"
        "li a7, 93;"
        "addi a0, %[num], 0;"
        "ecall;"
        : /* output: none */
        : [testnum]"r"(testnum), [num]"r"(num) /* input */
        : /* clobbers: none */
        // This point will not be executed, system exit function will be called with no return
        );
}