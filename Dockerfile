FROM openjdk:8u181-jre-alpine
WORKDIR /opt/dapp
COPY ./dapp-core/build/libs/dapp-core-all.jar /opt/dapp/dapp.jar
ADD ./configs /opt/dapp/configs

## THE LIFE SAVER
ADD https://github.com/ufoscout/docker-compose-wait/releases/download/2.5.0/wait /wait
## Launch the wait tool and then the application
ADD ./dapp_run.sh /dapp_run.sh
RUN chmod +x /wait
ENTRYPOINT ["sh","/dapp_run.sh"]
