FROM python:3
COPY requirements.txt /tmp
COPY facerec /usr/src/app/facerec

RUN apt-get update && apt-get install -y python3-opencv
RUN pip install opencv-python

WORKDIR /tmp
RUN pip install -r requirements.txt
WORKDIR /usr/src/app/facerec
RUN python manage.py makemigrations trainer && python manage.py migrate

EXPOSE 8009/tcp
ENV PATH=/usr/local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

CMD ["gunicorn","facerec.wsgi:application","--bind","0.0.0.0:8009","--workers=1"]

