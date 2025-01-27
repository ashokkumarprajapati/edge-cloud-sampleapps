from django.urls import path
from django.conf import settings
from django.conf.urls.static import static

from . import views

urlpatterns = [
    path('', views.index, name='index'),
    path('train/', views.train, name='train'),
    path('download/', views.download, name='download'),
    path('add/', views.add, name='add'),
    path('remove/', views.remove, name='remove'),
    path('lastupdate/', views.lastupdate, name='lastupdate'),
    path('init/', views.init, name='init'),
    path('predict/', views.predict, name='predict'),
    path('test/', views.test_connection, name='test_connection'),
]+ static(settings.STATIC_URL, document_root=settings.STATIC_ROOT)
