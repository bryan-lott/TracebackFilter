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
```

## TODO
* User-defined "fenceposts" for what to extract (regexes?)
* Reading from STDIN
* Writing to STDOUT
* Writing to something other than SNS

## Contributing
Fork + Branch and create a PR, open an issue, or start a conversation!
