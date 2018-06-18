
source ./common.sh
export EM4R_BUILD=./em4r-build
export EM4R_DOWNLOAD=./em4r-download
export EM4R_SERIAL=/dev/ttyACM0



echo ""
echo ""
echo "**************************************************************"
echo "* Little River Research and Design EM4R Software Build Setup *"
echo "**************************************************************"
echo ""

function usage() {
	echo "Recommend adding the following to your ~./bashrc file:"
	echo ""
	echo "export EM4R_BUILD=~/em4r-build"
	echo "export EM4R_DOWNLOAD=~/em4r-downloads"
	echo "export EM4R_SERIAL=/dev/ttyACM0"
	exit 1
}

BUILDDIR=${EM4R_BUILD}
if [ "${BUILDDIR}" == "" ]; then
	echo "Must export EM4R_BUILD containing build path (recommend $HOME/em4r-build)."
	usage
fi
echo "Build directory will be" ${BUILDDIR}

DLDIR=${EM4R_DOWNLOAD}
if [ "${DLDIR}" == "" ]; then
	echo "Must export EM4R_DOWNLOAD containing build path (recommend $HOME/em4r-download)."
	usage
fi
echo "Build directory will be" ${DLDIR}

# Get the absolute path to the parent directory of this script.
SCRIPT=$(readlink -f $0)
SRCDIR=$(dirname ${SCRIPT})
echo "Source for source, patches and configurations will be" ${SRCDIR}

echo ""

# Deal with build directory.
if [ "$1" != "" ]; then
	if [ ! -d ${BUILDDIR} -o ! -f ${BUILDDIR}/.build ]; then
		echo "Build directory does not exist or is not set up.  Re-run without an argument."
		exit 1
	fi
else
	# Make the build directory and the downloads directory.
	if [ -d $BUILDDIR ]; then
		echo "Build directory already exists."
		while true; do
			echo ""
			echo "The build directory $BUILDDIR will be cleared.  Any source changes will be lost."
			read -p "Continue? [yN] " yn
			case $yn in
				[Yy]* ) break;;
				[Nn]* ) exit;;
				"" ) exit;;
				* ) echo "Please answer y or n.";;
			esac
		done
	echo ""
	fi

	# Note that the build directory may be a symbolic link.
	_set_tag "setup:          "
	if [ -d ${BUILDDIR} ]; then
		echo "Clearing build directory." | _tag
		rm -rf ${BUILDDIR}/* 2>&1 | _tag
	fi
	if [ ! -d ${BUILDDIR} ]; then
		echo "Creating build directory." | _tag
		mkdir -p ${BUILDDIR} 2>&1 | _tag
	fi

	# Leave a file in the build directory to let the scripts know it's okay to run there.
	echo "(Re)creating sentinel file in build directory." | _tag
	touch $BUILDDIR/.build 2>&1 | _tag

	# Set up a link from the build directory to the persistent downloads directory.
	echo "Linking from build directory to persistent downloads directory." | _tag
	rm -f ${BUILDDIR}/downloads 2>&1 | _tag
	ln -fs ${DLDIR} ${BUILDDIR}/downloads 2>&1 | _tag

	# Set up a link from the build directory to the Makefile.
	echo "Linking from build directory to top-level Makefile." | _tag
	ln -fs ${SRCDIR}/arduino/common/Makefile ${BUILDDIR}/Makefile 2>&1 | _tag

	# Build the scripts directory.
	echo "Creating scripts directory in build hierarchy." | _tag
	rm -rf ${BUILDDIR}/scripts/ 2>&1 | _tag
	mkdir -p ${BUILDDIR}/scripts/ 2>&1 | _tag

#	echo "Linking to scripts in source hierarchy." | _tag
#	mkdir -p ${BUILDDIR}/scripts/ 2>&1 | _tag
#	ln -fs ${SRCDIR}/scripts/*.sh ${BUILDDIR}/scripts/ 2>&1 | _tag
fi

### Arduino firmware
if [ "$1" == "" -o "$1" == "arduino-code" ]; then
	HANDLED=1
	_set_tag  "arduino-code:   "

	echo "Recreating directory in build hierarchy." | _tag
	rm -rf ${BUILDDIR}/arduino 2>&1 | _tag
        mkdir -p ${BUILDDIR}/arduino 2>&1 | _tag

	echo "Installing link to common directory in source hierarchy." | _tag
	ln -s ${SRCDIR}/arduino/common/ ${BUILDDIR}/arduino/common

	# Find all library subdirectories.
	pushd ${SRCDIR}/arduino/libs 2>&1 > /dev/null
		subdirs=`find ${SRCDIR}/arduino/libs -mindepth 1 -type d`
		subdirs=`basename -a ${subdirs}`
	popd 2>&1 > /dev/null
	for subdir in ${subdirs}
	do
		# Link to files in this project.
		echo "Creating ${subdir} library build directory and linking to source hierarchy." | _tag
		mkdir -p ${BUILDDIR}/arduino/libs/${subdir}
		ln -s ${SRCDIR}/arduino/common/Makefile-lib ${BUILDDIR}/arduino/libs/${subdir}/Makefile
		pushd ${SRCDIR}/arduino/libs/${subdir} 2>&1 > /dev/null
			targets=`find . -name \*.inc -o -name \*.S -o -name \*.h -o -name \*.c -o -name \*.cpp`
		popd 2>&1 > /dev/null
		targets=`basename -a ${targets}`
		for target in ${targets}
		do
			echo "  " $target | _tag
			ln -s ${SRCDIR}/arduino/libs/${subdir}/${target} ${BUILDDIR}/arduino/libs/${subdir}
		done
	done

	# Find all application subdirectories.
	pushd ${SRCDIR}/arduino/apps 2>&1 > /dev/null
		subdirs=`find ${SRCDIR}/arduino/apps -mindepth 1 -type d`
		subdirs=`basename -a ${subdirs}`
	popd 2>&1 > /dev/null
	for subdir in ${subdirs}
	do
		# Link to files in this project.
		echo "Creating ${subdir} application build directory and linking to source hierarchy." | _tag
		mkdir -p ${BUILDDIR}/arduino/apps/${subdir}
		ln -s ${SRCDIR}/arduino/common/Makefile-app ${BUILDDIR}/arduino/apps/${subdir}/Makefile
		pushd ${SRCDIR}/arduino/apps/${subdir} 2>&1 > /dev/null
			targets=`find . -name \*.inc -o -name \*.S -o -name \*.h -o -name \*.c -o -name \*.cpp`
		popd 2>&1 > /dev/null
		targets=`basename -a ${targets}`
		for target in ${targets}
		do
			echo "  " $target | _tag
			ln -s ${SRCDIR}/arduino/apps/${subdir}/${target} ${BUILDDIR}/arduino/apps/${subdir}
		done
	done
fi
