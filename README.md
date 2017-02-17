# TracebackFilter

Tails log files and filters for python-style Tracebacks, sending them to an AWS SNS topic.

## Usage via Bash/Java

```bash
export TBF_SNS_TOPIC="arn-to-your-sns-topic-here"
export TBF_SNS_SUBJECT="Your SNS subject Prefix here"
java -jar tracebackfilter.jar /path/to/your/logfile/here.log /path/to/another/logfile/to/monitor/here.log
```

## Usage via Docker

```bash
docker pull mystickphoenix/tracebackfilter
docker run -d -v /path/to/your/log/folder/here:/log -e TBF_SNS_TOPIC="arn-to-your-sns-topic-here" -e TBF_SNS_SUBJECT="Your SNS subject Prefix here" mystickphoenix/tracebackfilter /log/your-logfile-name-here.log /log/additional-logfiles-to-monitor.log
```

When specifying the docker args, the -v should map a path to the folder of your logfile.  The two -e env args are required and should be filled out with your specific information.

The final unnamed arguments are the local-to-docker path of the logfiles.  Note that you can specify multiple logfiles to track.

If, in the -v argument you mapped /log/specialfolder:/log and wanted to tail /log/specialfolder/super-secret.log you would then specify /log/super-secret.log as the final argument.

## Additional Arguments

* `--before`
  * Type: integer
  * Purpose: Define the number of log lines before a traceback to include.
  * Default: 0
* `--after`
  * Type: integer
  * Purpose: Define the number of log lines after a traceback to include.
  * Default: 0
* `--disable-start-message`
  * Type: flag/boolean
  * Purpose: Disable the inital sending of "Now tracking the following logfiles:..."
  * Default false

## Additional Environment Variables

* `TBF_START_REGEX`
  * Type: string (regular expression)
  * Purpose: When this regex matches a given log line, start capturing log output.
  * Default: `^Traceback`
* `TBF_END_REGEX`
  * Type: string (regular expression)
  * Purpose: When this regex doesn't match a given log line, stop capturing log output.
  * Default: `^\s+|^Traceback|^[\w\.]+:\s+|^DETAIL|^$|^Attempt \d+ failed`

## TODO

* Unix-style Piping
  * Reading from STDIN
  * Writing to STDOUT
* Writing to something other than SNS

## Contributing
Fork + Branch and create a PR, open an issue, or start a conversation!
