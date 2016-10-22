# TracebackFilter
Tails a log file and filters for python-style Tracebacks, sending them to an AWS SNS topic

## Usage
```bash
export TBF_SNS_TOPIC="arn-to-your-sns-topic-here"
export TBF_SNS_SUBJECT="your message subject"
java -jar tracebackfilter.jar /path/to/your/logfile/here
```

## Dockerhub
```bash
docker pull mystickphoenix/tracebackfilter
```



