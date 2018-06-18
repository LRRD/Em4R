#pragma once

// A private struct to contain implementation data.
// A private pointer to an instance of this structure.
// A private copy constructor to prevent copying.
// A private assignment operator to prevent assignment.

#define DECLARE_IMPL(CLASS_) \
private: \
	struct Impl; \
	Impl* impl; \
	CLASS_(const CLASS_&); \
	CLASS_& operator=(const CLASS_&);
