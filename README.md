# TracebackFilter
Tails a log file and filters for python-style Tracebacks, sending them to an AWS SNS topic

## Usage
```bash
export TBF_SNS_TOPIC="arn-to-your-sns-topic-here"
export TBF_SNS_SUBJECT="Your SNS subject Prefix here"
java -jar tracebackfilter.jar /path/to/your/logfile/here.log
```

## Dockerhub
```bash
docker pull mystickphoenix/tracebackfilter
docker run -d -v /path/to/your/logfile/here.log:/log/monitor.log -e TBF_SNS_TOPIC="arn-to-your-sns-topic-here" -e "Your SNS subject Prefix here" mystickphoenix/tracebackfilter
docker run -d -v /path/to/your/log/folder/here:/log -e TBF_SNS_TOPIC="arn-to-your-sns-topic-here" -e "Your SNS subject Prefix here" mystickphoenix/tracebackfilter /log/your-logfile-name-here.log
```

When specifying the docker args, the -v should map a path to the folder of your logfile.  The two -e env args are required and should be filled out with your specific information.  

The final unnamed argument is the local-to-docker path of the logfile.  So, if in the -v argument you mapped /log/specialfolder:/log and wanted to tail super-secret.log you would then specify /log/super-secret.log as the final argument.

## TODO
* User-defined "fenceposts" for what to extract (regexes?)
* Reading from STDIN
* Writing to STDOUT
* Writing to something other than SNS

## Contributing
Fork + Branch and create a PR, open an issue, or start a conversation!
