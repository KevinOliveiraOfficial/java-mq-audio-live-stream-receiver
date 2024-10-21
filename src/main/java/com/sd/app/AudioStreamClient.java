package com.sd.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class AudioStreamClient {
    private String currentRadioChannel = "SERTANEJO"; // Canal padrão
    private SourceDataLine line; // Para controlar a linha de áudio
    private List<String> channels;

    private Connection connection;
    private Channel currentChannel;

    public AudioStreamClient() throws IOException, TimeoutException
    {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        this.connection = factory.newConnection();

        this.listenChannels();
    }

    private void listenChannels()
    {
        try
        {
            // RabbitMQ config
            final String QUEUE_NAME = "CHANNEL_LIST";
            Channel channel = this.connection.createChannel();
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);

            System.out.println("Waiting channel list...");
            DeliverCallback deliverCallback = (consumerTag, delivery) ->
            {
                try
                {
                    List<String> channels = new ArrayList<String>();
                    String message = new String(delivery.getBody(), "UTF-8");

                    // Dividir a string por ";"
                    String[] parts = message.split(";");
                    
                    // Exibir os itens separados
                    for (String part : parts) {
                        channels.add(part);
                    }

                    this.channels = channels;
                    //System.out.println(" [x] Received '" + message + "'");
                } catch (Exception e) {
                    System.err.println("Erro ao processar a mensagem: " + e.getMessage());
                    e.printStackTrace();
                }
            };

            channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> {});
        }
        catch ( IOException e )
        {
            System.out.println(e.getMessage());
        }
    }

    private void showChannels()
    {
        for ( String channel : this.channels )
        {
            System.out.println(channel);
        }
    }

    private void playChannel()
    {
        try
        {
            // RabbitMQ config
            final String QUEUE_NAME = this.currentRadioChannel;
            this.currentChannel = this.connection.createChannel();
            this.currentChannel.queueDeclare(QUEUE_NAME, false, false, false, null);

            System.out.println("Playing channel: " + QUEUE_NAME);
            
            try
            {
                AudioFormat format = new AudioFormat(44100, 16, 2, true, false); // Formato de áudio
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();
                DeliverCallback deliverCallback = (consumerTag, delivery) ->
                {
                    if ( line.isOpen() == false )
                        return;
                    
                    // Get audio data chunk
                    byte[] audioData = delivery.getBody();

                    if ( audioData != null )
                    {
                        line.write(audioData, 0, audioData.length);
                    }
                };

                this.currentChannel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> {});
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }
        catch ( IOException e )
        {
            System.out.println(e.getMessage());
        }
    }

    private void changeChannel(String newChannel) {
        currentRadioChannel = newChannel;
        System.out.println("Canal trocado para: " + newChannel);
        stopPlaying();
        startPlaying(); // Inicia a reprodução automaticamente ao trocar de canal
    }

    private void stopPlaying()
    {
        try
        {
            if (line != null && line.isOpen()) {
                //line.drain();
                //line.stop();
                line.close();
            }
            System.out.println("Stopped line");
            if ( this.currentChannel != null && this.currentChannel.isOpen() )
            {
                this.currentChannel.close();
            }
            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TimeoutException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void startPlaying() {
        this.playChannel();
        //new Thread(this::playChannel).start(); // Inicia a reprodução em uma nova thread
    }

    public void init()
    {
        try
        {
            Scanner scanner = new Scanner(System.in);
            boolean exit = false;

            while (!exit) {
                System.out.println("\nMenu:");
                System.out.println("1. Mostrar canais disponíveis");
                System.out.println("2. Escolher canal");
                System.out.println("3. Trocar para o próximo canal");
                System.out.println("4. Parar de tocar");
                System.out.println("5. Sair");
                System.out.print("Escolha uma opção: ");
                int choice = scanner.nextInt();
                scanner.nextLine();  // Consome a nova linha

                switch (choice) {
                    case 1:
                        this.showChannels();
                        break;
                    case 2:
                        System.out.print("Digite o nome do canal: ");
                        String channel = scanner.nextLine();
                        this.changeChannel(channel); // Muda o canal e já começa a tocar
                        break;
                    case 3:
                        List<String> channels = this.channels;
                        int currentIndex = channels.indexOf(this.currentRadioChannel);
                        if (currentIndex != -1) {
                            // Se for o último canal, volta para o primeiro
                            if (currentIndex == channels.size() - 1) {
                                this.changeChannel(channels.get(0));
                            } else {
                                this.changeChannel(channels.get(currentIndex + 1));
                            }
                        }
                        break;
                    case 4:
                        this.stopPlaying(); // Para a reprodução
                        System.out.println("Reprodução parada.");
                        break;
                    case 5:
                        this.stopPlaying(); // Certifica-se de parar a reprodução antes de sair
                        exit = true;
                        System.out.println("Saindo...");
                        break;
                    default:
                        System.out.println("Opção inválida.");
                        break;
                }
            }
            scanner.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
