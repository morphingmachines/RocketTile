Verilator
=================
* Follow this [link](https://verilator.org/guide/latest/install.html) for detailed instructions.
* Install Verilator using **Run-in-Place from `VERILATOR_ROOT`** installation option.

```sh
git clone https://github.com/verilator/verilator 
cd verilator
git tag                     # See what versions exit
#git checkout stable        # Use most recent release
#git checkout v{version}    # Switch to specified release version

autoconf # create ./configure script

export VERILATOR_ROOT=`pwd`/build
./configure
make -j$(nproc)
```
Add `$VERILATOR_ROOT/bin` to `PATH` environment variable.

Rocket-tools
==============================
We need the following software tools to run bare-metal examples on the RTL simulator. 
* [RISC-V GNU Cross Compiler](https://github.com/riscv-collab/riscv-gnu-toolchain.git)
* Front-End Server (FESVR), a C++ library that manages communication between a host machine and a RISC-V DUT, that is part of the [spike](https://github.com/riscv-software-src/riscv-isa-sim.git) build.
* [riscv-tests](https://github.com/riscv-software-src/riscv-tests.git), required for the start-up code for the baremetal environment.


For the pre-requisite list of OS-specific packages, refer to this [link](https://github.com/chipsalliance/rocket-tools/blob/ca6dc52742914ab5f9b7fd1444fa0ffbae9aa631/README.md?plain=1#L29-L36)

Follow the steps to install the required software tools.

* Clone the [rocket-tools](https://github.com/chipsalliance/rocket-tools.git) repository and the checkout the commit as mentioned in the [rocket-tools.hash](./../rocket-tools.hash)

```sh
git clone https://github.com/chipsalliance/rocket-tools.git
cd rocket-tools
git checkout <commit>
```

* Set `RISCV` environment variable to the install path.
```sh
export RISCV=`pwd`/../rocket-tools-install/
```

* Install spike
```sh
git submodule update --recursive --init riscv-isa-sim
cd riscv-isa-sim
mkdir build
cd build
../configure --prefix=$RISCV
make -j$(nproc)
make install
```

* Install RISC-V GNU cross compiler with newlib from sources. This takes some time.
```sh
git submodule update --recursive --init riscv-gnu-toolchain
cd riscv-gnu-toolchain
mkdir build
../configure --prefix=$RISCV --with-arch=rv32imf --with-abi=ilp32
#../configure --prefix=$RISCV --with-arch=rv64imfd --with-abi=lp64
make -j$(nproc)
```

* Clone riscv-tests and set `RISCV_TESTS_SRC` environment variable.
```sh
git submodule update --recursive --init riscv-tests
export RISCV_TESTS_SRC=`pwd`/riscv-tests
```

* Add `$RISCV/bin` to `PATH` environment variable.

<!---
Crosstool-NG (alternative method)
===================================
This is alternative to build from sources as shown in [Rocket-Tools](#rocket-tools).
Use [crosstool-ng](https://crosstool-ng.github.io/) to build RISC-V cross compiler.

Steps to install **crosstool-ng** 
```sh
git clone https://github.com/crosstool-ng/crosstool-ng
cd crosstool-ng
./bootstrap
./configure --prefix=`pwd`/build
make
make install
```
Find **`ct-ng`** at `crosstool-ng/build/bin/` directory.

* Use [riscv64-unknown-elf-ct-ng.config](./riscv64-unknown-elf-ct-ng.config) file to build the cross compiler. 
* It is assumed that `RISCVCC` environment variable is set to the RISC-V GCC cross compiler install path. 
* The `ct-ng` will install riscv-gnu-toolchain package at `$RISCVCC/riscv64-unknown-elf/` directory.


Steps to build the cross compiler
```sh
mv riscv64-unknown-elf-ct-ng.config .config
unset LD_LIBRARY_PATH
./ct-ng build
```

Add `$RISCV/riscv64-unknown-elf/bin` to `PATH` environment variable.
-->
