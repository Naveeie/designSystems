#!/bin/bash

#$1 == tag_name

#docker system prune -a
docker build -t uassist-summary:latest -f Dockerfile .
docker tag uassist-summary:latest registry.uniphore.com/uniphore-manual/uassist-summary:$1
docker push registry.uniphore.com/uniphore-manual/uassist-summary:$1