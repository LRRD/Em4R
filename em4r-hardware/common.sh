# Support functions.

# Script exits on first error, and any failure in a pipe is a failure of the pipe.
set -e
set -o pipefail

# Tagged-line support.

# Call _set_tag with quoted string with which _tag should prefix output lines.
function _set_tag()
{
	TAG="$1"

	# Compute the number of characters available in a terminal line, following the tag
	TAG_CHARS=`expr length "${TAG}"`
	COLS=`tput cols`
	CUT_CHARS=$((${COLS} - ${TAG_CHARS}))

	# sed extended regex to cut line down to CUT_CHARS chars
	TAG_EXPR_1="s/^(.{"${CUT_CHARS}"}).*$/\1/"

	# sed regex to insert tag in front of line
	TAG_EXPR_2="s/^/"${TAG}"/"
}

# Output a line, clipped to the terminal width, with a preceding tag string.
# -u required to ensure non buffering since the first sed is writing to a pipe, not a terminal.
function _tag() {
	sed -u -r "${TAG_EXPR_1}" | sed -e "${TAG_EXPR_2}"
}
