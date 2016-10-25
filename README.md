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
docker run -d --rm -v /path/to/your/logfile/here.log:/log/monitor.log -e TBF_SNS_TOPIC="arn-to-your-sns-topic-here" -e "Your SNS subject Prefix here" mystickphoenix/tracebackfilter
```
