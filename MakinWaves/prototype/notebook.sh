NAME="makin-waves-notebook"
docker rm -f "${NAME}"
docker run -d --name "${NAME}" -v "$(pwd):/home/jovyan" -w /home/jovyan --user "$(id -u):$(id -g)" \
       -p 8888:8888 -e JUPYTER_ENABLE_LAB=yes \
       jupyter/scipy-notebook:dc9744740e12
sleep 3
docker logs "${NAME}"
