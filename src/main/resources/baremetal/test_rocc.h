
// RISC-V ABI, a0-a7 registers are used for passing function arguments.
// for 'ecall' (Linux system calls)  a7 specifies the Linux function number in unistd.h.
// 93 is the Linux service call number to terminate the program.

static inline __attribute__((always_inline)) void exit_pass(void)
{
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

static inline __attribute__((always_inline)) void exit_fail(int num)
{
    if (num == 0)
    {
        num = -1;
    }

    int testnum = num * 2 + 1;

    __asm__ volatile(
        "fence;"
        "addi gp, %[testnum], 0;"
        "li a7, 93;"
        "addi a0, %[num], 0;"
        "ecall;"
        :                                        /* output: none */
        : [testnum] "r"(testnum), [num] "r"(num) /* input */
        :                                        /* clobbers: none */
        // This point will not be executed, system exit function will be called with no return
    );
}
