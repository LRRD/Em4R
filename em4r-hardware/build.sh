#!/bin/sh

echo ""
echo ""
echo "********************************************************"
echo "* Little River Research and Design EM4R Software Build *"
echo "********************************************************"
echo ""

function usage() {
	echo "Recommend adding the following to your ~./bashrc file:"
	echo ""
	echo "export EM4R_BUILD=~/em4r-build"
	echo "export EM4R_SERIAL=/dev/ttyACM0"
	exit 1
}

BUILDDIR=${EM4R_BUILD}
if [ "${BUILDDIR}" == "" ]; then
	echo "Must export EM4R_BUILD containing build path (recommend $HOME/em4r-build)."
	usage
	exit 1
fi
echo "Build directory will be" ${BUILDDIR}

echo ""

# Deal with build directory.
if [ ! -d ${BUILDDIR} -o ! -f ${BUILDDIR}/.build ]; then
	echo "Build directory does not exist or is not set up.  Run setup.sh."
	exit 1
fi

# Run the build script.
pushd ${BUILDDIR}
	make clean
	make all
popd
