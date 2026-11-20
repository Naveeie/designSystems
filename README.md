# summary



## Getting started

To make it easy for you to get started with GitLab, here's a list of recommended next steps.

Already a pro? Just edit this README.md and make it your own. Want to make it easy? [Use the template at the bottom](#editing-this-readme)!

### Compile the code (without testcases)

```
cd ~/<git-repo>/
./mvnw clean package -DskipTests=true
```

### Create docker images 

```
cd ~/<git-repo>/
./docker-build.sh 2.2.2.0
```


## Garden Deployment
```
cd ~/<git-repo>/
./garden deploy --env=dev
```
