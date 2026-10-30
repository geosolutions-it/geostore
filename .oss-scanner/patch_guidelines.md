# How we would like patches made

- As narrow as possible: fix the faulty code path, no refactoring around it.
- With a unit test, a JUnit test in the module's `src/test`, that fails without the fix and passes with it.
- Follow the style of the surrounding code; comments only where the code is not obvious.
- Against the `master` branch.
