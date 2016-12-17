# Change Log

## [2016.12.xx][unreleased]
### Changed
- Add environment vars for user configurable fenceposts:
  - `TBF_START_REGEX`
  - `TBF_END_REGEX`
  - `TBF_SKIP_REGEX`
- Reduce the size of the uberjar by removing extra amazonica modules
- Fix subject extraction bug
- Change versioning scheme from semantic to time-based

## [2016.10.27](https://github.com/bryan-lott/TracebackFilter/releases/tag/v2016.10.27)
### Fixed
- Instead of following a single log file endlessly, TF now plays nicely with logrotate and stays on the primary log.
- Bugs squashed

## [2016.10.22](https://github.com/bryan-lott/TracebackFilter/releases/tag/v2016.10.22)
### Initial Release
