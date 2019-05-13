FROM openjdk:8-jre
WORKDIR /opt/brvs
COPY ./dapp-core/build/libs/iroha-dapp-all.jar /opt/dapp/dapp.jar

## THE LIFE SAVER
ADD https://github.com/ufoscout/docker-compose-wait/releases/download/2.5.0/wait /wait
## Launch the wait tool and then the application
ADD ./dapp_run.sh /dapp_run.sh
ENTRYPOINT ["sh","/dapp_run.sh"]
